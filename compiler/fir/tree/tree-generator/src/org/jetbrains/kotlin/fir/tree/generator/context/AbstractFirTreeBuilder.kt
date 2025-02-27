/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.tree.generator.context

import org.jetbrains.kotlin.fir.tree.generator.model.Element
import org.jetbrains.kotlin.fir.tree.generator.model.IntermediateBuilder
import org.jetbrains.kotlin.fir.tree.generator.printer.BASE_PACKAGE
import org.jetbrains.kotlin.utils.DummyDelegate
import org.jetbrains.kotlin.generators.tree.ClassRef
import org.jetbrains.kotlin.generators.tree.PositionTypeParameterRef
import org.jetbrains.kotlin.generators.tree.TypeRef
import org.jetbrains.kotlin.generators.tree.TypeKind
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class AbstractFirTreeBuilder {
    companion object {
        val baseFirElement = Element(
            "Element",
            Element.Kind.Other
        )
    }

    val elements = mutableListOf(baseFirElement)
    val intermediateBuilders = mutableListOf<IntermediateBuilder>()

    protected fun element(kind: Element.Kind, vararg dependencies: Element): ElementDelegateProvider {
        return ElementDelegateProvider(kind, dependencies, isSealed = false, predefinedName = null)
    }

    protected fun sealedElement(kind: Element.Kind, vararg dependencies: Element): ElementDelegateProvider {
        return ElementDelegateProvider(kind, dependencies, isSealed = true, predefinedName = null)
    }

    protected fun element(name: String, kind: Element.Kind, vararg dependencies: Element): ElementDelegateProvider {
        return ElementDelegateProvider(kind, dependencies, isSealed = false, predefinedName = name)
    }

    protected fun sealedElement(name: String, kind: Element.Kind, vararg dependencies: Element): ElementDelegateProvider {
        return ElementDelegateProvider(kind, dependencies, isSealed = true, predefinedName = name)
    }

    private fun createElement(name: String, kind: Element.Kind, vararg dependencies: Element): Element =
        Element(name, kind).also {
            if (dependencies.isEmpty()) {
                it.parentRefs.add(baseFirElement)
            }
            it.parentRefs.addAll(dependencies)
            elements += it
        }

    private fun createSealedElement(
        name: String,
        kind: Element.Kind,
        vararg dependencies: Element,
    ): Element {
        return createElement(name, kind, *dependencies).apply {
            isSealed = true
        }
    }

    val configurations: MutableMap<Element, () -> Unit> = mutableMapOf()

    fun applyConfigurations() {
        for (element in elements) {
            configurations[element]?.invoke()
        }
    }

    inner class ElementDelegateProvider(
        private val kind: Element.Kind,
        private val dependencies: Array<out Element>,
        private val isSealed: Boolean,
        private val predefinedName: String?
    ) {
        operator fun provideDelegate(
            thisRef: AbstractFirTreeBuilder,
            prop: KProperty<*>
        ): ReadOnlyProperty<Any?, Element> {
            val name = predefinedName ?: prop.name.replaceFirstChar { it.uppercaseChar() }
            val element = if (isSealed) {
                createSealedElement(name, kind, *dependencies)
            } else {
                createElement(name, kind, *dependencies)
            }
            return DummyDelegate(element)
        }
    }
}

fun generatedType(type: String, kind: TypeKind = TypeKind.Class): ClassRef<PositionTypeParameterRef> = generatedType("", type, kind)

fun generatedType(packageName: String, type: String, kind: TypeKind = TypeKind.Class): ClassRef<PositionTypeParameterRef> {
    val realPackage = BASE_PACKAGE + if (packageName.isNotBlank()) ".$packageName" else ""
    return type(realPackage, type, exactPackage = true, kind = kind)
}

fun type(
    packageName: String,
    type: String,
    exactPackage: Boolean = false,
    kind: TypeKind = TypeKind.Interface,
): ClassRef<PositionTypeParameterRef> {
    val realPackage = if (exactPackage) packageName else packageName.let { "org.jetbrains.kotlin.$it" }
    return org.jetbrains.kotlin.generators.tree.type(realPackage, type, kind)
}

inline fun <reified T : Any> type() = org.jetbrains.kotlin.generators.tree.type<T>()
