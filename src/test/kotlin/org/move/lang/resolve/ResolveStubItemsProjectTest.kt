package org.move.lang.resolve

//class ResolveStubItemsProjectTest : ResolveProjectTestCase() {
//    fun `test resolve module stubs only from other file in sources folder`() = stubOnlyResolve {
//        namedMoveToml("MyPackage")
//        sources {
//            move(
//                "module.move", """
//            module 0x1::Module {}
//                      //X
//            """
//            )
//            move(
//                "main.move", """
//            script {
//                use 0x1::Module;
//                       //^
//            }
//            """
//            )
//        }
//    }
//
//}
