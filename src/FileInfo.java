import java.io.Serializable;
import java.util.ArrayList;

class FileInfo implements Serializable {
    /**
     Klasa zawiera informacje o konkretnym pliku
     */
    private static final long serialVersionUID = -460270693247182695L;
    private final String name;
    // private long downloaded;
    private final ArrayList<ConnectInfo> peerList = new ArrayList<>();
    private final String checksum;
    private long size;
    private String filePath;
    private final long[] downloaded=new long[peerList.size()];
    private FileState state;

    FileInfo(String name, long size, ConnectInfo peer, String checksum) {
        this.name = name;
        this.size = size;
        this.checksum = checksum;
        this.peerList.add(peer);
    }

    public FileInfo(String name, long size, String path, String checksum) {
        this.name = name;
        this.size = size;
        this.filePath = path;
        this.checksum = checksum;
        this.state = FileState.UPLOADING;
    }


    String getName() {
        return name;
    }

    String getChecksum() {
        return checksum;
    }

    ArrayList<ConnectInfo> getPeerList() {
        return peerList;
    }

    void addPeer(ConnectInfo peer) {
        if (!this.findDuplicatePeer(peer)) {
            this.peerList.add(peer);
        }
    }

    public String toString() {
        return "File: " + this.name + "\n" +
                "Size: " + this.size + "bites" + "\n" +
                "Peers: " + this.peerList.size() + "\n" +
                "Checksum: " + this.checksum + "\n";
    }

    private boolean findDuplicatePeer(ConnectInfo find) {
        for (ConnectInfo p : peerList) {
            if (p.getPort() == (find.getPort())) {
                return true;
            }
        }
        return false;
    }

    public long getSize() {
        return size;
    }


    public void setSize(long size) {
        this.size = size;
    }

    public FileState getState() {
        return state;
    }

    public void setState(FileState uploading) {
        this.state = uploading;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setDownloaded(int i, long downloaded) {
        this.downloaded[i]=downloaded;
    }

    public long[] getDownloaded() {
        return downloaded;
    }

}
