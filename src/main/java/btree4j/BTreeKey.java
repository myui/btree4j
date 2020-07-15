package btree4j;

public class BTreeKey {
    private Value key;
    private Long pointer;

    public BTreeKey(Value key, Long pointer) {
        this.key = key;
        this.pointer = pointer;
    }

    public Value getKey() {
        return key;
    }

    public Long getPointer() {
        return pointer;
    }
}
