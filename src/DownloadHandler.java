import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class DownloadHandler {


    void startDownloading(FileInfo file, String storage, Database local_db, JTable table) {
        final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(10);
        Thread fileChecker = new Thread(new fileWorker(storage, local_db, table));
        //  fileChecker.run();

        Runnable serverTask = () -> {

            try {

                int i = 0;
                for (ConnectInfo c : file.getPeerList()) {
                    Socket cSocket = new Socket(c.getIp(), c.getPort());
                    clientProcessingPool.submit(new ClientTask(cSocket, file, i, file.getPeerList().size() - 1, new PointerPos(file, i, file.getPeerList().size()), storage, fileChecker));
                    System.out.println("Conected to peer: " + c.getIp() + " Port: " + c.getPort());
                    i++;
                }


            } catch (IOException e) {
                System.err.println("Unable to process client request");
                e.printStackTrace();
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();


    }

    //tworzy sume kontrolną MD5
    private String getChecksum(String file) throws NoSuchAlgorithmException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] block = new byte[4096];
            int length;
            while ((length = in.read(block)) > 0) {
                digest.update(block, 0, length);
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void deleteTMP(List<File> files) {
        for (File f : files) {
            try {
                Thread.sleep(2000);
                Files.delete(f.toPath());
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

    }


    //tworzy paczke danych z zaszytymi informacjami

    private byte[] CreateDataPacket(byte[] cmd, byte[] data) {
        byte[] packet = null;
        try {
            byte[] initialize = new byte[1];
            initialize[0] = 2;
            byte[] separator = new byte[1];
            separator[0] = 4;
            byte[] data_length = String.valueOf(data.length).getBytes("UTF8");
            packet = new byte[initialize.length + cmd.length + separator.length + data_length.length + data.length];

            System.arraycopy(initialize, 0, packet, 0, initialize.length);
            System.arraycopy(cmd, 0, packet, initialize.length, cmd.length);
            System.arraycopy(data_length, 0, packet, initialize.length + cmd.length, data_length.length);
            System.arraycopy(separator, 0, packet, initialize.length + cmd.length + data_length.length, separator.length);
            System.arraycopy(data, 0, packet, initialize.length + cmd.length + data_length.length + separator.length, data.length);

        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
        return packet;
    }
    //tworzy paczke danych z zaszytymi informacjami

    private class ClientTask implements Runnable {
        private final Socket clientSocket;
        private final String storage;
        private final FileInfo fileInfo;
        Thread thread;
        int partID;
        long startPoint;
        long endPoint;
        private int lastPart;
        private DataInputStream din;
        private DataOutputStream dout;
        private PrintWriter out;

        private ClientTask(Socket clientSocket, FileInfo file, String storage) {
            this.clientSocket = clientSocket;

            this.storage = storage;
            this.fileInfo = file;
        }

        ClientTask(Socket clientSocket, FileInfo file, int i, int lastPart, PointerPos position, String storage, Thread thread) {
            this.clientSocket = clientSocket;
            this.partID = i;
            this.lastPart = lastPart;
            this.startPoint = position.getStartPoint();
            if (lastPart != 0) {
                this.endPoint = position.getEndPoint();
            } else {
                this.endPoint = file.getSize();

            }
            this.storage = storage;
            this.fileInfo = file;
            this.thread = thread;

        }

        @Override
        public void run() {

            System.out.println("Got a peer!");


            try {
                out = new PrintWriter(clientSocket.getOutputStream());

                String fileReq = fileInfo.getName();
                out.println(fileReq);               //wysłanie nazwy pliku którego chcemy pobrać
                out.flush();


                out.println(startPoint);               //wysłanie jaką czesc pliku którego chcemy pobrać
                out.flush();

                out.println(endPoint);               //wysłanie jaką czesc pliku którego chcemy pobrać
                out.flush();

                din = new DataInputStream(clientSocket.getInputStream());
                dout = new DataOutputStream(clientSocket.getOutputStream());

                RandomAccessFile rw = null;
                long current_file_pointer = startPoint;

                if (partID > 0) {
                    current_file_pointer = startPoint - 1;
                }
                File f;
                if (lastPart > 0) {
                    f = new File(storage + "\\" + fileInfo.getName() + "." + partID);
                    if (f.exists() && !f.isDirectory()) {
                        current_file_pointer = new RandomAccessFile(storage + "\\" + fileInfo.getName() + "." + partID, "r").length();
                    }


                } else {
                    f = new File(storage + "\\" + fileInfo.getName());
                    if (f.exists() && !f.isDirectory()) {
                        current_file_pointer = new RandomAccessFile(storage + "\\" + fileInfo.getName(), "r").length();
                    }
                }


                boolean loop_break = false;
                while (!loop_break && fileInfo.getState() == FileState.DOWNLOADING) {
                    byte[] initilize = new byte[1];
                    try {
                        din.read(initilize, 0, initilize.length);
                        if (initilize[0] == 2) {
                            byte[] cmd_buff = new byte[3];
                            din.read(cmd_buff, 0, cmd_buff.length);
                            byte[] recv_data = ReadStream();
                            switch (Integer.parseInt(new String(cmd_buff))) {
                                case 124:
                                    if (lastPart > 0) {
                                        rw = new RandomAccessFile(storage + "\\" + fileInfo.getName() + "." + partID, "rw");
                                    } else {
                                        rw = new RandomAccessFile(storage + "\\" + fileInfo.getName() + ".", "rw");
                                    }
                                    dout.write(CreateDataPacket("125".getBytes("UTF8"), String.valueOf(current_file_pointer).getBytes("UTF8")));
                                    dout.flush();
                                    break;
                                case 126:


                                    rw.seek(Math.abs(current_file_pointer - startPoint));
                                    rw.write(recv_data);
                                    current_file_pointer = rw.getFilePointer() + startPoint;


                                    System.out.println("Download percentage: " + ((float) Math.abs(startPoint - current_file_pointer) / endPoint) * 100 + "%" + "    partID: " + partID);
                                    dout.write(CreateDataPacket("125".getBytes("UTF8"), String.valueOf(current_file_pointer).getBytes("UTF8")));
                                    dout.flush();
                                    break;
                                case 127:
                                    if ("Close".equals(new String(recv_data))) {
                                        loop_break = true;

                                    }
                                    break;
                            }
                        }
                        if (loop_break) {
                            System.out.println("file  downloaded part: " + partID);
                            fileInfo.setFilePath(storage + "\\" + fileInfo.getName());

                            if (lastPart == 0) {
                                if (fileInfo.getChecksum().equals(getChecksum(storage + "\\" + fileInfo.getName()))) {
                                    System.out.println("md5 sie zgadza");
                                } else {
                                    System.out.println("md5 sie nie zgadza");
                                }
                            } else {
                                if (listOfFilesToMerge(storage + "\\" + fileInfo.getName() + "." + 0, lastPart) != null) {
                                    System.out.println("file merge");
                                    mergeFiles(storage + "\\" + fileInfo.getName() + "." + 0, storage + "\\" + fileInfo.getName(), lastPart);


                                    if (fileInfo.getChecksum().equals(getChecksum(storage + "\\" + fileInfo.getName()))) {
                                        System.out.println("md5 sie zgadza");

                                    } else {
                                        System.out.println("md5 sie nie zgadza");
                                    }

                                }
                            }
                            din.close();
                            dout.close();
                            out.close();
                            rw.close();
                            clientSocket.close();

                            thread.run();
                            deleteTMP(listOfFilesToMerge(storage + "\\" + fileInfo.getName() + "." + 0, lastPart));
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                        //  } catch (InterruptedException e) {
                        //      e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        List<File> listOfFilesToMerge(String oneOfFiles, int numOfFiles) {
            return listOfFilesToMerge(new File(oneOfFiles), numOfFiles);
        }

        void mergeFiles(String oneOfFiles, String into, int numOfFiles) throws IOException {
            mergeFiles(new File(oneOfFiles), new File(into), numOfFiles);
        }

        void mergeFiles(List<File> files, File into)
                throws IOException {
            try (FileOutputStream fos = new FileOutputStream(into);
                 BufferedOutputStream mergingStream = new BufferedOutputStream(fos)) {
                for (File f : files) {
                    Files.copy(f.toPath(), mergingStream);
                    mergingStream.flush();
                    fos.flush();
                }
                if (mergingStream == null && fos == null) {
                    fos.close();
                    mergingStream.close();
                }
            }

        }

        void mergeFiles(File oneOfFiles, File into, int numOfFiles)
                throws IOException {
            mergeFiles(listOfFilesToMerge(oneOfFiles, numOfFiles), into);
        }

        List<File> listOfFilesToMerge(File oneOfFiles, int numOfFiles) {
            String tmpName = oneOfFiles.getName();
            String destFileName = tmpName.substring(0, tmpName.lastIndexOf('.'));//remove .{number}
            File[] files = oneOfFiles.getParentFile().listFiles(
                    (File dir, String name) -> name.matches(destFileName + "[.]\\d+"));
            Arrays.sort(files);
            if (files.length == numOfFiles + 1) {
                return Arrays.asList(files);
            }
            return null;
        }

        //znajduję ścieżkę pliku do wysłąnia
        private byte[] ReadStream() {
            byte[] data_buff = null;
            try {
                int b = 0;
                String buff_length = "";
                while ((b = din.read()) != 4) {
                    buff_length += (char) b;
                }
                int data_length = Integer.parseInt(buff_length);
                data_buff = new byte[Integer.parseInt(buff_length)];
                int byte_read = 0;
                int byte_offset = 0;
                while (byte_offset < data_length) {
                    byte_read = din.read(data_buff, byte_offset, data_length - byte_offset);
                    byte_offset += byte_read;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return data_buff;
        }


    }


    private class fileWorker implements Runnable {
        private final Database local_db;
        JTable table;
        private String storage;

        public fileWorker(String storage, Database local_db, JTable table) {
            this.storage = storage;
            this.local_db = local_db;
            this.table = table;
        }

        @Override
        public void run() {


            File f;

            for (FileInfo p : local_db.getFile()) {
                f = new File(storage + "\\" + p.getName());
                if (f.exists()) {
                    p.setState(FileState.DONE);
                    ((DefaultTableModel) table.getModel()).setRowCount(0);
                    addToTable(local_db, table);
                }

            }


        }

        private void addToTable(Database db, JTable table) {
            int numCols = table.getModel().getColumnCount();
            ((DefaultTableModel) table.getModel()).setRowCount(0);
            Object[] data = new Object[numCols];
            int i = 0;
            for (FileInfo bd : db.getFile()) {
                if (numCols == 4) {
                    data[0] = i;
                    data[1] = bd.getName();
                    data[2] = bd.getSize() + " bytes";
                    data[3] = bd.getPeerList().size();
                    i++;
                    ((DefaultTableModel) table.getModel()).addRow(data);
                } else {
                    data[0] = i;
                    data[1] = bd.getName();
                    data[2] = bd.getSize() + " bytes";
                    data[3] = bd.getState();
                    data[4] = bd.getPeerList().size();

                    i++;
                    ((DefaultTableModel) table.getModel()).addRow(data);
                }

            }

            i = 0;
        }
    }
}





