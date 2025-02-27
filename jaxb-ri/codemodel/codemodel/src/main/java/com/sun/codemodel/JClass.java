/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.codemodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a Java reference type, such as a class, an interface,
 * an enum, an array type, a parameterized type.
 * 
 * <p>
 * To be exact, this object represents an "use" of a reference type,
 * not necessarily a declaration of it, which is modeled as {@link JDefinedClass}.
 */
public abstract class JClass extends JType {

    protected JClass( JCodeModel _owner ) {
        this._owner = _owner;
    }

    /**
     * Gets the name of this class.
     *
     * @return
     *	name of this class, without any qualification.
     *	For example, this method returns "String" for
     *  {@code java.lang.String}.
     */
    @Override
    abstract public String name();
	
	/**
     * Gets the package to which this class belongs.
     * TODO: shall we move move this down?
     */
    abstract public JPackage _package();

    /**
     * Returns the class in which this class is nested, or {@code null} if
     * this is a top-level class.
     */
    public JClass outer() {
        return null;
    }
	
    private final JCodeModel _owner;
    /** Gets the JCodeModel object to which this object belongs. */
    @Override
    public final JCodeModel owner() { return _owner; }
    
    /**
     * Gets the super class of this class.
     * 
     * @return
     *      Returns the JClass representing the superclass of the
     *      entity (class or interface) represented by this .
     *      Even if no super class is given explicitly or this
     *      is not a class, this method still returns
     *       for {@link Object}.
     *      If this JClass represents {@link Object}, return null.
     */
    abstract public JClass _extends();
    
    /**
     * Iterates all super interfaces directly implemented by
     * this class/interface.
     * 
     * @return
     *		A non-null valid iterator that iterates all
     *         objects that represents those interfaces
     *		implemented by this object.
     */
    abstract public Iterator<JClass> _implements();
    
    /**
     * Iterates all the type parameters of this class/interface.
     * 
     * <p>
     * For example, if this  represents
     * {@code Set<T>}, this method returns an array
     * that contains single {@link JTypeVar} for 'T'.
     */
    public JTypeVar[] typeParams() {
        return EMPTY_ARRAY;
    }

    /**
     * Sometimes useful reusable empty array.
     */
    protected static final JTypeVar[] EMPTY_ARRAY = new JTypeVar[0];

    /**
     * Checks if this object represents an interface.
     */
    abstract public boolean isInterface();

    /**
     * Checks if this class is an abstract class.
     */
    abstract public boolean isAbstract();

    /**
     * If this class represents one of the wrapper classes
     * defined in the java.lang package, return the corresponding
     * primitive type. Otherwise null.
     */
    public JPrimitiveType getPrimitiveType() { return null; }

    @Deprecated
    @Override
    public JClass boxify() { return this; }

    @Override
    public JType unboxify() {
        JPrimitiveType pt = getPrimitiveType();
        return pt==null ? this : pt;
    }

    @Override
    public JClass erasure() {
        return this;
    }

    /**
     * Checks the relationship between two classes.
     * <p>
     * This method works in the same way as {@link Class#isAssignableFrom(Class)}
     * works. For example, baseClass.isAssignableFrom(derivedClass)==true.
     */
    public final boolean isAssignableFrom( JClass derived ) {
        // to avoid the confusion, always use "this" explicitly in this method.
        
        // null can be assigned to any type.
        if( derived instanceof JNullType )  return true;
        
        if( this==derived )     return true;
        
        // the only class that is assignable from an interface is
        // java.lang.Object
        if( this==_package().owner().ref(Object.class) )  return true;
        
        JClass b = derived._extends();
        if( b!=null && this.isAssignableFrom(b) )
            return true;
        
        if( this.isInterface() ) {
            Iterator<JClass> itfs = derived._implements();
            while( itfs.hasNext() )
                if( this.isAssignableFrom(itfs.next()) )
                    return true;
        }
        
        return false;
    }

    /**
     * Gets the parameterization of the given base type.
     *
     * <p>
     * For example, given the following
     * <pre>{@code
     * interface Foo<T> extends List<List<T>> {}
     * interface Bar extends Foo<String> {}
     * }</pre>
     * This method works like this:
     * <pre>{@code
     * getBaseClass( Bar, List ) = List<List<String>
     * getBaseClass( Bar, Foo  ) = Foo<String>
     * getBaseClass( Foo<? extends Number>, Collection ) = Collection<List<? extends Number>>
     * getBaseClass( ArrayList<? extends BigInteger>, List ) = List<? extends BigInteger>
     * }</pre>
     *
     * @param baseType
     *      The class whose parameterization we are interested in.
     * @return
     *      The use of {@code baseType} in {@code this} type.
     *      or null if the type is not assignable to the base type.
     */
    public final JClass getBaseClass( JClass baseType ) {

        if( this.erasure().equals(baseType) )
            return this;

        JClass b = _extends();
        if( b!=null ) {
            JClass bc = b.getBaseClass(baseType);
            if(bc!=null)
                return bc;
        }

        Iterator<JClass> itfs = _implements();
        while( itfs.hasNext() ) {
            JClass bc = itfs.next().getBaseClass(baseType);
            if(bc!=null)
                return bc;
        }

        return null;
    }

    public final JClass getBaseClass( Class<?> baseType ) {
        return getBaseClass(owner().ref(baseType));
    }


    private JClass arrayClass;
    @Override
    public JClass array() {
        if(arrayClass==null)
            arrayClass = new JArrayClass(owner(),this);
        return arrayClass;
    }

    /**
     * "Narrows" a generic class to a concrete class by specifying
     * a type argument.
     * 
     * <p>
     * {@code .narrow(X)} builds {@code Set<X>} from {@code Set}.
     */
    public JClass narrow( Class<?> clazz ) {
        return narrow(owner().ref(clazz));
    }

    public JClass narrow( Class<?>... clazz ) {
        JClass[] r = new JClass[clazz.length];
        for( int i=0; i<clazz.length; i++ )
            r[i] = owner().ref(clazz[i]);
        return narrow(r);
    }

    /**
     * "Narrows" a generic class to a concrete class by specifying
     * a type argument.
     * 
     * <p>
     * {@code .narrow(X)} builds {@code Set<X>} from {@code Set}.
     */
    public JClass narrow( JClass clazz ) {
        return new JNarrowedClass(this,clazz);
    }

    public JClass narrow( JType type ) {
        return narrow(type.boxify());
    }

    public JClass narrow( JClass... clazz ) {
        return new JNarrowedClass(this,Arrays.asList(clazz.clone()));
    }

    public JClass narrow( List<? extends JClass> clazz ) {
        return new JNarrowedClass(this,new ArrayList<>(clazz));
    }

    /**
     * If this class is parameterized, return the type parameter of the given index.
     */
    public List<JClass> getTypeParameters() {
        return Collections.emptyList();
    }

    /**
     * Returns true if this class is a parameterized class.
     */
    public final boolean isParameterized() {
        return erasure()!=this;
    }

    /**
     * Create "? extends T" from T.
     *
     * @return never null
     */
    public final JClass wildcard() {
        return new JTypeWildcard(this);
    }

    /**
     * Substitutes the type variables with their actual arguments.
     * 
     * <p>
     * For example, when this class is {@code Map<String,Map<V>>},
     * (where V then doing
     * substituteParams( V, Integer ) returns a
     * for {@code Map<String,Map<Integer>>}.
     * 
     * <p>
     * This method needs to work recursively.
     */
    protected abstract JClass substituteParams( JTypeVar[] variables, List<JClass> bindings );
    
    @Override
    public String toString() {
        return this.getClass().getName() + '(' + name() + ')';
    }


    public final JExpression dotclass() {
        return JExpr.dotclass(this);
    }

    /** Generates a static method invocation. */
    public final JInvocation staticInvoke(JMethod method) {
        return new JInvocation(this,method);
    }
    
    /** Generates a static method invocation. */
    public final JInvocation staticInvoke(String method) {
        return new JInvocation(this,method);
    }
    
    /** Static field reference. */
    public final JFieldRef staticRef(String field) {
        return new JFieldRef(this, field);
    }

    /** Static field reference. */
    public final JFieldRef staticRef(JVar field) {
        return new JFieldRef(this, field);
    }

    @Override
    public void generate(JFormatter f) {
        f.t(this);
    }

    /**
     * Prints the class name in javadoc @link format.
     */
    void printLink(JFormatter f) {
        f.p("{@link ").g(this).p('}');
    }
}
