package org.onedatashare.server.model.core;


import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

@Data
public abstract class Resource<S extends Session<S, R>, R extends Resource<S, R>> {
  public final String path;
  public final S session;

  protected Resource(S session, String path) {
    if (path == null)
      path = "/";
    this.path = path;
    this.session = session;
  }

  protected Resource(S session) {
    this(session, null);
  }

  public abstract Mono<R> select(String path);

  public final Mono<R> initialize() {
    return session.initialize().then(Mono.just((R) this));
  }

  public Mono<Stat> stat() {
    throw unsupported("stat");
  }

  public Mono<Stat> stat(String folderId) {
    throw unsupported("stat");
  }

  public Flux<String> list() {
    throw unsupported("list");
  }

  public Mono<R> mkdir() {
    throw unsupported("mkdir");
  }

  public Mono<R> delete() {
    throw unsupported("delete");
  }

  public Tap tap() {
    throw unsupported("tap");
  }

  public Drain sink() {
    throw unsupported("sink");
  }

  private UnsupportedOperationException unsupported(String op) {
    throw new UnsupportedOperationException(
            "The " + op + " operation is unsupported.");
  }

  public Mono<R> reselectOn(S session) {
    if (session == null)
      throw new IllegalArgumentException();
    if (session == this.session)
      return Mono.just((R)this);
    if (!session.equals(this.session))
      throw new IllegalArgumentException();
    return session.select(path);
  }
}
