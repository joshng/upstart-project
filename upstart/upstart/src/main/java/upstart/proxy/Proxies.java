// Modified or written by Lambdascale SRL for inclusion with lambdaj.
// Copyright (c) 2009-2010 Mario Fusco.
// Licensed under the Apache License, Version 2.0 (the "License")

package upstart.proxy;


import org.mockito.Mockito;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class Proxies {

  private Proxies() {
  }

  /**
   * Creates a dynamic proxy
   *
   * @param clazz                The class to be proxied
   * @param interceptor          The interceptor that manages the invocations to the created proxy
   * @return The newly created proxy
   */
  @SuppressWarnings("unchecked")
  public static <T> T createProxy(Class<T> clazz, InvocationHandler interceptor) {
    return clazz.isInterface()
            ? (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, interceptor)
            : Mockito.mock(clazz, invocation -> interceptor.invoke(invocation.getMock(), invocation.getMethod(), invocation.getArguments()));
  }
}
