interface InterfaceWithConcreteMethods {
    void nonShared(Integer i = 0, Integer j = i) {}
    shared void shared(Integer i = 0, Integer j = i) {}
    shared default void default(Integer i = 0, Integer j = i) {}
    shared formal void formal(Integer i = 0, Integer j = i);
}
