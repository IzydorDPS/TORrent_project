class PointerPos {
    private final int iloscHostow;
    private final FileInfo file;
    private final int id;

    public PointerPos(FileInfo file, int id, int iloscHostow) {
        this.file = file;
        this.id = id;
        this.iloscHostow = iloscHostow;
    }

    public long getStartPoint() {
        long x = this.file.getSize() / this.iloscHostow;
      //  long y = (long) Math.ceil(x);             //ile bitów przypada na hosta
        int y = (int) (this.file.getSize() - x < x ? this.file.getSize() - x : x);

        if (this.id == 0) {
            return this.id * y;
        }
        return this.id * y ;
    }

    public long getEndPoint() {
        long x = this.file.getSize() / this.iloscHostow;
       // long y = (long) Math.ceil(x);             //ile bitów przypada na hosta
        int y = (int) (this.file.getSize() - x < x ? this.file.getSize() - x : x);
        return (this.id + 1) * y;
    }
}
