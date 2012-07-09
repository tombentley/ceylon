package ceylon.language;

import com.redhat.ceylon.compiler.java.metadata.Ceylon;
import com.redhat.ceylon.compiler.java.metadata.Ignore;

@Ignore
@Ceylon(major = 2)
class StringOfNone extends String implements None<Character> {
    
    static StringOfNone instance = new StringOfNone();
    
    private StringOfNone() { 
        super(""); 
    }
    
    @Override
    public long getSize() {
        return 0;
    }
    
    @Override
    public boolean getEmpty() {
        return true;
    }
    
    @Override
    public Character getFirst() {
        return null;
    }
    @Override public Character getLast() {
        return null;
    }

    @Override
    public Iterable<? extends Character> getRest() {
        return this;
    }

    @Override 
    @Ignore
    public Iterable<? extends Character> getSequence() { 
        return this; 
    }
    @Override @Ignore
    public Character find(Callable<? extends Boolean> f) {
        return null;
    }
    @Override @Ignore
    public Character findLast(Callable<? extends Boolean> f) {
        return null;
    }
    @Override 
    @Ignore
    public Iterable<? extends Character> sorted(Callable<? extends Comparison> f) {
        return this;
    }
    @Override 
    @Ignore
    public <Result> Iterable<Result> map(Callable<? extends Result> f) { 
        return new MapIterable<Character, Result>(this, f); 
    }
    @Override 
    @Ignore
    public Iterable<? extends Character> filter(Callable<? extends Boolean> f) { 
        return this; 
    }
    @Override 
    @Ignore
    public <Result> Result fold(Result ini, Callable<? extends Result> f) { 
        return ini;
    }
    @Override @Ignore
    public boolean any(Callable<? extends Boolean> f) {
        return false;
    }
    @Override @Ignore
    public boolean every(Callable<? extends Boolean> f) {
        return false;
    }
    @Override @Ignore
    public Iterable<? extends Character> skipping(long skip) {
        return this;
    }
    @Override @Ignore
    public Iterable<? extends Character> taking(long take) {
        return this;
    }
    @Override @Ignore
    public Iterable<? extends Character> by(long step) {
        return this;
    }
    @Override @Ignore
    public long count(Callable<? extends Boolean> f) {
        return 0;
    }
    @Override @Ignore
    public Iterable<? extends Character> getCoalesced() { return this; }
    @Override @Ignore
    public Iterable<? extends Entry<? extends Integer, ? extends Character>> getIndexed() { return (Iterable)this; }
    @Override @Ignore public <Other>Iterable chain(Iterable<? extends Other> other) { return other; }

    @Override @Ignore public <Other>String withLeading() { return this; }
    @Override @Ignore public <Other>String withTrailing() { return this; }
    @Ignore @Override public <Other>Iterable<? extends Other> withLeading$elements() { return (Iterable)this; }
    @Ignore @Override public <Other>Iterable<? extends Other> withTrailing$elements() { return (Iterable)this; }

    @Override @Ignore public <Other>List withLeading(Iterable<? extends Other> elements) {
        return $array.array(elements);
    }
    @Override @Ignore public <Other>List withTrailing(Iterable<? extends Other> elements) {
        return $array.array(elements);
    }
}
