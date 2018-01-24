import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class UploadHandler {


    public void startServer(int port, String storage, Database db) {
        final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(10);


        //

        Runnable serverTask = () -> {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Waiting for clients to connect...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    clientProcessingPool.submit(new ClientTask(clientSocket, storage, db));
                }
            } catch (IOException e) {
                System.err.println("Unable to process client request");
                e.printStackTrace();
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();

    }

    //znajduję ścieżkę pliku do wysłąnia
    private FileInfo findFileToSend(String peerReq, Database db) {
        for (FileInfo p : db.getFile()) {
            if ((p.getName().equals(peerReq) && (p.getState() == FileState.UPLOADING))) {
                return p;
            }
        }
        return null;
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

    //odczytuje przychodzący strumień danych
    private byte[] ReadStream(DataInputStream din) {
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
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return data_buff;
    }

    private class ClientTask implements Runnable {
        private final Socket clientSocket;
        private final String storage;
        private final Database db;


        private ClientTask(Socket clientSocket, String storage, Database db) {
            this.clientSocket = clientSocket;
            this.storage = storage;
            this.db = db;
        }

        @Override
        public void run() {
            System.out.println("Got a client !");


            try {
                DataInputStream din = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());

                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));


                String peerReq = in.readLine();
                System.out.println("client want :" + peerReq);              //odczytanie co klient chce pobrać


                long startPoint = Long.parseLong(in.readLine());
                System.out.println("client want download from pozition: " + startPoint);    //odczytanie jaką część tego pliku klient chce pobrać


                long endPoint = Long.parseLong(in.readLine());                              //odczytanie jaką część tego pliku klient chce pobrać
                System.out.println("client want download to position: " + endPoint);


                File myFile = new File(findFileToSend(peerReq, db).getFilePath());            //znajduje plik do pobrania w swojej liście plików


                dout.write(CreateDataPacket("124".getBytes("UTF8"), myFile.getName().getBytes("UTF8")));    //wysyła paczke inicjującą pobieranie
                dout.flush();
                RandomAccessFile rw = new RandomAccessFile(myFile, "r");
                long current_file_pointer;
                boolean loop_break = false;
                while (!loop_break && findFileToSend(peerReq, db).getState() == FileState.UPLOADING) {      //pętla obsługująca wysyłanie danych
                    if (din.read() == 2) {
                        byte[] cmd_buff = new byte[3];
                        din.read(cmd_buff, 0, cmd_buff.length);
                        byte[] recv_buff = ReadStream(din);
                        switch (Integer.parseInt(new String(cmd_buff))) {
                            case 125:
                                current_file_pointer = Long.valueOf(new String(recv_buff)); //odbiera od klienta informację o aktualnym file pointer
                                int buff_len = (int) (endPoint - current_file_pointer < 1024 ? endPoint - current_file_pointer : 1024);
                                byte[] temp_buff = new byte[buff_len];
                                if (current_file_pointer != endPoint) {
                                    rw.seek(current_file_pointer);
                                    rw.read(temp_buff, 0, temp_buff.length);
                                    dout.write(CreateDataPacket("126".getBytes("UTF8"), temp_buff));
                                    dout.flush();
                                    //System.out.println("Upload percentage: " + ((float) current_file_pointer / rw.length()) * 100 + "%");
                                } else {
                                    loop_break = true;
                                }
                                break;
                        }
                    }
                    if (loop_break) {
                        System.out.println("Stop Server informed");
                        dout.write(CreateDataPacket("127".getBytes("UTF8"), "Close".getBytes("UTF8"))); //wysłanie pakietu informującego klienta o zakończeniu wysyłania
                        dout.flush();
                        clientSocket.close();
                        System.out.println("Client Socket Closed");
                        break;
                    }
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }


    }

}


