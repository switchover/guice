/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class InjectorTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface Other {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface S {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface I {}

  public void testProviderMethods() throws CreationException {
    SampleSingleton singleton = new SampleSingleton();
    SampleSingleton other = new SampleSingleton();

    BinderImpl builder = new BinderImpl();
    builder.bind(SampleSingleton.class).toInstance(singleton);
    builder.bind(SampleSingleton.class)
        .annotatedWith(Other.class)
        .toInstance(other);
    Injector injector = builder.createInjector();

    assertSame(singleton,
        injector.getProvider(Key.get(SampleSingleton.class)).get());
    assertSame(singleton, injector.getProvider(SampleSingleton.class).get());

    assertSame(other,
        injector.getProvider(Key.get(SampleSingleton.class, Other.class)).get());
  }

  static class SampleSingleton {}

  public void testInjection() throws CreationException {
    Injector injector = createFooInjector();
    Foo foo = injector.getProvider(Foo.class).get();

    assertEquals("test", foo.s);
    assertEquals("test", foo.bar.getTee().getS());
    assertSame(foo.bar, foo.copy);
    assertEquals(5, foo.i);
    assertEquals(5, foo.bar.getI());

    // Test circular dependency.
    assertSame(foo.bar, foo.bar.getTee().getBar());
  }

  private Injector createFooInjector() throws CreationException {
    return Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Bar.class).to(BarImpl.class);
        bind(Tee.class).to(TeeImpl.class);
        bindConstant(S.class).to("test");
        bindConstant(I.class).to(5);
      }
    });
  }

  public void testGetInstance() throws CreationException {
    Injector injector = createFooInjector();

    Bar bar = injector.getProvider(Key.get(Bar.class)).get();
    assertEquals("test", bar.getTee().getS());
    assertEquals(5, bar.getI());
  }

  public void testIntAndIntegerAreInterchangeable()
      throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bindConstant(I.class).to(5);
    Injector injector = builder.createInjector();
    IntegerWrapper iw = injector.getProvider(IntegerWrapper.class).get();
    assertEquals(5, (int) iw.i);
  }

  static class IntegerWrapper {
    @Inject @I Integer i;
  }

  static class Foo {

    @Inject Bar bar;
    @Inject Bar copy;

    @Inject @S String s;

    int i;

    @Inject
    void setI(@I int i) {
      this.i = i;
    }
  }

  interface Bar {

    Tee getTee();
    int getI();
  }

  @Singleton
  static class BarImpl implements Bar {

    @Inject @I int i;

    Tee tee;

    @Inject
    void initialize(Tee tee) {
      this.tee = tee;
    }

    public Tee getTee() {
      return tee;
    }

    public int getI() {
      return i;
    }
  }

  interface Tee {

    String getS();
    Bar getBar();
  }

  static class TeeImpl implements Tee {

    final String s;
    @Inject Bar bar;

    @Inject
    TeeImpl(@S String s) {
      this.s = s;
    }

    public String getS() {
      return s;
    }

    public Bar getBar() {
      return bar;
    }
  }

  public void testCircularlyDependentConstructors()
      throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bind(A.class).to(AImpl.class);
    builder.bind(B.class).to(BImpl.class);

    Injector injector = builder.createInjector();
    A a = injector.getProvider(AImpl.class).get();
    assertNotNull(a.getB().getA());
  }

  interface A {
    B getB();
  }

  @Singleton
  static class AImpl implements A {
    final B b;
    @Inject public AImpl(B b) {
      this.b = b;
    }
    public B getB() {
      return b;
    }
  }

  interface B {
    A getA();
  }

  static class BImpl implements B {
    final A a;
    @Inject public BImpl(A a) {
      this.a = a;
    }
    public A getA() {
      return a;
    }
  }

  public void testInjectStatics() throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bindConstant(S.class).to("test");
    builder.bindConstant(I.class).to(5);
    builder.requestStaticInjection(Static.class);
    builder.createInjector();

    assertEquals("test", Static.s);
    assertEquals(5, Static.i);
  }

  static class Static {

    @Inject @I static int i;

    static String s;

    @Inject static void setS(@S String s) {
      Static.s = s;
    }
  }

  public void testPrivateInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("foo");
        bind(int.class).toInstance(5);
      }
    });

    Private p = injector.getProvider(Private.class).get();
    assertEquals("foo", p.fromConstructor);
    assertEquals(5, p.fromMethod);
  }

  static class Private {
    String fromConstructor;
    int fromMethod;

    @Inject
    private Private(String fromConstructor) {
      this.fromConstructor = fromConstructor;
    }

    @Inject
    private void setInt(int i) {
      this.fromMethod = i;
    }
  }

  public void testProtectedInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("foo");
        bind(int.class).toInstance(5);
      }
    });

    Protected p = injector.getProvider(Protected.class).get();
    assertEquals("foo", p.fromConstructor);
    assertEquals(5, p.fromMethod);
  }

  static class Protected {
    String fromConstructor;
    int fromMethod;

    @Inject
    protected Protected(String fromConstructor) {
      this.fromConstructor = fromConstructor;
    }

    @Inject
    protected void setInt(int i) {
      this.fromMethod = i;
    }
  }
}