// MODULE: m1-common
// FILE: common.kt

expect open class Foo {
    fun foo()
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

actual open <!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING!>class Foo<!> {
    actual fun foo() {}

    fun <!NON_ACTUAL_MEMBER_DECLARED_IN_EXPECT_NON_FINAL_CLASSIFIER_ACTUALIZATION_WARNING!>foo<!>(overloaded: Int) {} // accidential override can happen with this injected fun. That's why it's prohibited
}
