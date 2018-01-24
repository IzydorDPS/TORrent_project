import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

class Tracker {
    private static final Database db = new Database();      //lista przechowywanych plików

    public static void main(String[] args) {
        // TODO Auto-generated method stub


        int serverPort = Integer.parseInt(args[0]);
        try {
            ServerSocket server = new ServerSocket(serverPort);
            System.out.println("Tracker run...");

            while (true) {
                new Thread(new ClientWorker(server.accept(), db)).start();

                System.out.println("Connected with client");

            }
        } catch (IOException e) {
            System.out.println("Could not listen on port: " + serverPort);
            e.printStackTrace();
            System.exit(-1);
        }
    }

}

class ClientWorker implements Runnable {
    private Socket client;
    private ObjectOutputStream oout;
    private ObjectInputStream oin;
    private Database db;


    ClientWorker(Socket rec_socket, Database opdb) {
        try {
            this.db = opdb;
            client = rec_socket;
            oout = new ObjectOutputStream(client.getOutputStream());
            oin = new ObjectInputStream(client.getInputStream());


        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void run() {
        while (true) {


            try {
                FileInfo object = (FileInfo) oin.readObject();
                if (object != null) {
                    db.addFileInfo(object);                                             //dodaje obiekt do listy plików
                    System.out.println("Server: " + object.getName() + " recived");
                    System.out.println(db.getFileList());

                    break;
                }
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }


            System.out.println("Sending file list to client");
            try {
                oout.writeObject(this.db);                                      //wysyła liste plików
                oout.close();
                client.close();
                break;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                System.exit(-1);
            }


        }

    }

}
