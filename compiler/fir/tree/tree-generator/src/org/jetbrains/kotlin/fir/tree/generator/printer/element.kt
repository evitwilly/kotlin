/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.tree.generator.printer

import org.jetbrains.kotlin.fir.tree.generator.context.AbstractFirTreeBuilder
import org.jetbrains.kotlin.fir.tree.generator.model.Element
import org.jetbrains.kotlin.fir.tree.generator.model.Field
import org.jetbrains.kotlin.fir.tree.generator.pureAbstractElementType
import org.jetbrains.kotlin.fir.tree.generator.util.get
import org.jetbrains.kotlin.generators.tree.*
import org.jetbrains.kotlin.generators.tree.printer.braces
import org.jetbrains.kotlin.generators.tree.printer.multipleUpperBoundsList
import org.jetbrains.kotlin.generators.tree.printer.typeParameters
import org.jetbrains.kotlin.utils.SmartPrinter
import org.jetbrains.kotlin.utils.withIndent
import java.io.File

fun Element.generateCode(generationPath: File): GeneratedFile {
    val dir = generationPath.resolve(packageName.replace(".", "/"))
    val file = File(dir, "$type.kt")
    val stringBuilder = StringBuilder()
    SmartPrinter(stringBuilder).apply {
        printCopyright()
        println("package $packageName")
        println()
        val imports = collectImports()
        imports.forEach { println("import $it") }
        if (imports.isNotEmpty()) {
            println()
        }
        printGeneratedMessage()
        printElement(this@generateCode)
    }
    return GeneratedFile(file, stringBuilder.toString())
}

fun SmartPrinter.printElement(element: Element) {
    with(element) {
        val isInterface = kind == ImplementationKind.Interface || kind == ImplementationKind.SealedInterface
        fun abstract() {
            if (!isInterface) {
                print("abstract ")
            }
        }

        fun override() {
            if (this != AbstractFirTreeBuilder.baseFirElement) {
                print("override ")
            }
        }

        print("${kind!!.title} $type")
        print(typeParameters())
        val needPureAbstractElement = !isInterface && !allParents.any { it.kind == ImplementationKind.AbstractClass || it.kind == ImplementationKind.SealedClass }
        val superTypesStrings = parentRefs.map {
            // TODO: Factor out
            var result = it.element.type
            if (it.args.isNotEmpty()) {
                result += it.args.values.joinToString(", ", "<", ">") { it.typeWithArguments }
            }
            result + it.element.kind.braces()
        } + additionalSupertypeInterfaces.map { it.type }

        if (superTypesStrings.isNotEmpty() || needPureAbstractElement) {
            print(" : ")
            if (needPureAbstractElement) {
                print("${pureAbstractElementType.type}()")
                if (superTypesStrings.isNotEmpty()) {
                    print(", ")
                }
            }
            print(superTypesStrings.joinToString(", "),)
        }
        print(multipleUpperBoundsList())
        println(" {")
        withIndent {
            allFields.forEach { field ->
                if (field.isFinal && field.fromParent || field.isParameter) return@forEach
                printField(field, isImplementation = false, override = field.fromParent) {
                    if (!field.isFinal) {
                        abstract()
                    }
                }
            }
            if (allFields.isNotEmpty()) {
                println()
            }

            override()
            println("fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R =")
            withIndent {
                println("visitor.visit${element.name}(this, data)")
            }

            println()
            println("@Suppress(\"UNCHECKED_CAST\")")
            override()
            println("fun <E : FirElement, D> transform(transformer: FirTransformer<D>, data: D): E =")
            withIndent {
                println("transformer.transform$name(this, data) as E")
            }

            fun Field.replaceDeclaration(override: Boolean, overridenType: TypeRef? = null, forceNullable: Boolean = false) {
                println()
                if (name == "source") {
                    println("@FirImplementationDetail")
                }
                abstract()
                if (override) print("override ")
                println(replaceFunctionDeclaration(overridenType, forceNullable))
            }

            allFields.filter { it.withReplace }.forEach {
                val override = overridenFields[it, it] &&
                        !(it.name == "source" && fullQualifiedName.endsWith("FirQualifiedAccessExpression"))
                it.replaceDeclaration(override, forceNullable = it.useNullableForReplace)
                for (overridenType in it.overridenTypes) {
                    it.replaceDeclaration(true, overridenType)
                }
            }

            for (field in allFields) {
                if (!field.needsSeparateTransform) continue
                println()
                abstract()
                if (field.fromParent && field.parentHasSeparateTransform) {
                    print("override ")
                }
                println(field.transformFunctionDeclaration(typeWithArguments))
            }
            if (needTransformOtherChildren) {
                println()
                abstract()
                if (element.parentRefs.any { it.element.needTransformOtherChildren }) {
                    print("override ")
                }
                println(transformFunctionDeclaration("OtherChildren", typeWithArguments))
            }

            if (element == AbstractFirTreeBuilder.baseFirElement) {
                require(isInterface) {
                    "$element must be an interface"
                }
                println()
                println("fun accept(visitor: FirVisitorVoid) = accept(visitor, null)")
                println()
                println("fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D)")
                println()
                println("fun acceptChildren(visitor: FirVisitorVoid) = acceptChildren(visitor, null)")
                println()
                println("fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement")
            }
        }
        println("}")
    }
}
