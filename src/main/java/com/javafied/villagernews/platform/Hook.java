package com.javafied.villagernews.platform;
public final class Hook<T> { public final java.util.List<T> listeners=new java.util.ArrayList<>(); public void register(T listener){listeners.add(listener);} }
