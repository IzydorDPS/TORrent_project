import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Database implements Serializable {
    private final List<FileInfo> FileList = Collections.synchronizedList(new ArrayList<FileInfo>());    //lista udostępnionych plików



    FileInfo getFile(int x) {
        return this.FileList.get(x);
    }
    List<FileInfo> getFile() {
        return this.FileList;
    }
    String getFileList() {
        return this.FileList.toString();
    }


    //Dodawanie nowych danych lo listy plików
    void addFileInfo(FileInfo newFile) {
        synchronized (this.FileList) {
            if (!findDuplicate(newFile.getName())) {
                this.FileList.add(newFile);
            } else {
                findDuplicate(newFile).addPeer(newFile.getPeerList().get(0));
            }
        }
    }

    private boolean findDuplicate(String find) {
        for (FileInfo p : this.FileList) {
            if (p.getName().equals(find)) {
                return true;
            }
        }
        return false;
    }

    private FileInfo findDuplicate(FileInfo find) {
        for (FileInfo p : this.FileList) {
            if (p.getName().equals(find.getName())) {
                return p;
            }
        }
        return null;
    }


}
