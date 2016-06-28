interface SuperWithinSyntheticClass {
    shared default Integer a => 0;
    shared default Integer m() => 0; 
}
interface SuperWithinSyntheticClass2 satisfies SuperWithinSyntheticClass {
    void f() {
        // those not within synthetic class, actually
        value a1 = super.a;
        super.m();
        super.m{};
        // but these are
        void local() {
            value a1 = super.a;
            super.m();
            super.m{};
        }
        value ref = super.m;
        value it = { super.m() };
        value com = { for (x in [super.m()]) x };
    }
}