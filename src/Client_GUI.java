import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

class Client_GUI {


    private static final Database local_db = new Database();
    /**
     * Create the application.
     */

    private static Database tracker_db = new Database();
    private static JTable table;
    private static JTable table_1;
    private final String clientID;
    private final String storage;
    // Tracker information
    private final String serverAddress = "localhost";
    private final int clientPort = findFreePort();
    // file/storage information
    //local info
    // ServerSocket server = new ServerSocket(clientPort);
    private final UploadHandler urh = new UploadHandler();
    private final DownloadHandler dh = new DownloadHandler();
    private int serverPort = 3439;
    private JFrame frmTorrentClient;
    private int currentRow;


    private Client_GUI(String[] id) {
        this.clientID = id[0];
        storage = "C:\\TORrent_" + id[0];
        new File(storage).mkdir();
        this.serverPort = Integer.parseInt(id[1]);
        initialize();

    }

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {

            try {
                Client_GUI window = new Client_GUI(args);
                window.frmTorrentClient.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    private static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore IOException on close()
            }
            return port;
        } catch (IOException ignored) {

        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port to start Server on");
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initialize() {
        frmTorrentClient = new JFrame();
        frmTorrentClient.setTitle("Torrent client " + clientID);
        frmTorrentClient.setBounds(100, 100, 450, 381);
        frmTorrentClient.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JMenuBar menuBar = new JMenuBar();
        frmTorrentClient.getContentPane().add(menuBar, BorderLayout.NORTH);

        JMenu mnNewMenu = new JMenu("File");
        menuBar.add(mnNewMenu);


        urh.startServer(clientPort, storage, local_db);


        JMenuItem Upload = new JMenuItem("Upload");
        Upload.addActionListener(arg0 -> {

            try {
                final JFileChooser fc = new JFileChooser();
                int returnVal = fc.showOpenDialog(null);

                Socket tracker = new Socket(serverAddress, serverPort);

                ObjectOutputStream oout = new ObjectOutputStream(tracker.getOutputStream());

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();


                    long bytes = new RandomAccessFile(file, "r").length();

                    oout.writeObject(new FileInfo(file.getName(), bytes, new ConnectInfo("localhost", clientPort), getChecksum(file.getPath())));

                    local_db.addFileInfo(new FileInfo(file.getName(), bytes, file.getPath(), getChecksum(file.getPath())));


                }
            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                System.out.println("Tracker connection problem");

                e.printStackTrace();
            } catch (IOException | NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            ((DefaultTableModel) table.getModel()).setRowCount(0);
            int numCols = table_1.getModel().getColumnCount();

            Object[] data = new Object[numCols];

            ((DefaultTableModel) table_1.getModel()).setRowCount(0);
            //  addToTable(local_db, table_1);
            int i = local_db.getFile().size();
            for (FileInfo db : local_db.getFile()) {

                data[0] = i;
                data[1] = db.getName();
                data[2] = db.getSize() + " bytes";
                data[3] = db.getState();
                data[4] = db.getPeerList().size();

                ((DefaultTableModel) table_1.getModel()).addRow(data);


            }
        });
        mnNewMenu.add(Upload);


        JButton btnRefreshTracker = new JButton("Refresh Tracker");
        btnRefreshTracker.addActionListener(arg0 -> {
            try {
                Socket tracker = new Socket(serverAddress, serverPort);

                ObjectOutputStream oout = new ObjectOutputStream(tracker.getOutputStream());
                ObjectInputStream oin = new ObjectInputStream(tracker.getInputStream());

                oout.writeObject(null);

                tracker_db = (Database) oin.readObject();
                System.out.println("Odebrano liste plików do pobrania");
                System.out.println("");


                System.out.println(tracker_db.getFileList());

                oin.close();
                tracker.close();

            } catch (IOException | ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            ((DefaultTableModel) table.getModel()).setRowCount(0);

            addToTable(tracker_db, table);

        });

        menuBar.add(btnRefreshTracker);

        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);
        panel.setBorder(null);
        frmTorrentClient.getContentPane().add(panel, BorderLayout.SOUTH);

        JLabel lblInfoLabel = new JLabel("This Client " + myIP());
        lblInfoLabel.setHorizontalAlignment(SwingConstants.LEFT);
        panel.add(lblInfoLabel);

        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBorder(null);
        frmTorrentClient.getContentPane().add(tabbedPane, BorderLayout.CENTER);


        DefaultTableModel tableModel = new DefaultTableModel();
        table = new JTable(tableModel);


        JPopupMenu popupMenu = new JPopupMenu();
        ActionListener actionListener = new PopupActionListener();

        JMenuItem menuItemDownload = new JMenuItem("Download");
        JMenuItem menuItemStop = new JMenuItem("Stop");
        JMenuItem menuItemStart = new JMenuItem("Start");
        JMenuItem menuItemUpload = new JMenuItem("Upload");
        menuItemDownload.addActionListener(actionListener);
        menuItemStop.addActionListener(actionListener);
        menuItemStart.addActionListener(actionListener);
        menuItemUpload.addActionListener(actionListener);
        popupMenu.add(menuItemDownload);
        JPopupMenu popupMenu1 = new JPopupMenu();
        popupMenu1.add(menuItemStart);
        popupMenu1.add(menuItemStop);
        popupMenu1.add(menuItemUpload);
        table.setComponentPopupMenu(popupMenu);


        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                JTable x = table;

                int r = x.rowAtPoint(e.getPoint());
                if (r >= 0 && r < x.getRowCount()) {
                    x.setRowSelectionInterval(r, r);
                } else {
                    x.clearSelection();
                }

                int rowindex = x.getSelectedRow();
                if (rowindex < 0)
                    return;
                if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());

                }
                Point point = e.getPoint();
                currentRow = x.rowAtPoint(point);


            }
        });


        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setModel(new DefaultTableModel(
                new Object[][]{
                },
                new String[]{
                        "#", "Name", "Size", "Peers"
                }
        ) {
            final Class[] columnTypes = new Class[]{
                    Integer.class, String.class, Object.class, Object.class
            };

            public Class getColumnClass(int columnIndex) {
                return columnTypes[columnIndex];
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(23);

        //  tabbedPane.addTab("Tracker", null, table, null);


        table_1 = new JTable();
        JScrollPane jsp = new JScrollPane();
        JScrollPane jsp1 = new JScrollPane();
        jsp.setViewportView(table);
        jsp1.setViewportView(table_1);

        tabbedPane.add("Tracker", jsp);
        tabbedPane.add("Local", jsp1);


        table_1.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                JTable x = table_1;

                int r = x.rowAtPoint(e.getPoint());
                if (r >= 0 && r < x.getRowCount()) {
                    x.setRowSelectionInterval(r, r);
                } else {
                    x.clearSelection();
                }

                int rowindex = x.getSelectedRow();
                if (rowindex < 0)
                    return;
                if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());

                }
                Point point = e.getPoint();
                currentRow = x.rowAtPoint(point);


            }

        });
        table_1.setComponentPopupMenu(popupMenu1);
        table_1.setModel(new DefaultTableModel(
                new Object[][]{
                },
                new String[]{
                        "#", "Name", "Size", "Status", "Peers"
                }
        ) {
            final Class[] columnTypes = new Class[]{
                    Integer.class, String.class, String.class, Object.class, Object.class
            };

            public Class getColumnClass(int columnIndex) {
                return columnTypes[columnIndex];
            }
        });
        table_1.getColumnModel().getColumn(0).setResizable(true);


    }

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

    private ConnectInfo myIP() {
        InetAddress myIP = null;

        try {
            myIP = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new ConnectInfo(myIP.toString(), clientPort);
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


    private class PopupActionListener implements ActionListener {

        public void actionPerformed(ActionEvent actionEvent) {
            String button = actionEvent.getActionCommand();
            if (button == "Download") {
                downloadFile(tracker_db);

            }
            if (button == "Stop") {
                System.out.println("stop");
                local_db.getFile(currentRow).setState(FileState.STOP);

                ((DefaultTableModel) table_1.getModel()).setRowCount(0);
                addToTable(local_db, table_1);
            }
            if (button == "Start") {
                System.out.println("start");
                local_db.getFile(currentRow).setState(FileState.DOWNLOADING);
                downloadFile(local_db);
                ((DefaultTableModel) table_1.getModel()).setRowCount(0);
                addToTable(local_db, table_1);
            }
            if (button == "Upload") {
                System.out.println("upload");


                try {


                    Socket tracker = new Socket(serverAddress, serverPort);
                    ObjectOutputStream oout = new ObjectOutputStream(tracker.getOutputStream());

                    File file = new File(local_db.getFile(currentRow).getFilePath());

                    long bytes = new RandomAccessFile(file, "r").length();

                    oout.writeObject(new FileInfo(file.getName(), bytes, new ConnectInfo("localhost", clientPort), getChecksum(file.getPath())));


                    local_db.getFile(currentRow).setState(FileState.UPLOADING);
                    ((DefaultTableModel) table_1.getModel()).setRowCount(0);
                    addToTable(local_db, table_1);

                } catch (UnknownHostException e) {
                    // TODO Auto-generated catch block
                    System.out.println("Tracker connection problem");

                    e.printStackTrace();
                } catch (IOException | NoSuchAlgorithmException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        }

        private void downloadFile(Database db) {


            db.getFile(currentRow).setState(FileState.DOWNLOADING);
            local_db.addFileInfo(db.getFile(currentRow));
            addToTable(local_db, table_1);
            ((DefaultTableModel) table_1.getModel()).fireTableDataChanged();

            dh.startDownloading(db.getFile(currentRow), storage, local_db, table_1);


            ((DefaultTableModel) table_1.getModel()).setRowCount(0);
            addToTable(local_db, table_1);

        }
    }


}

