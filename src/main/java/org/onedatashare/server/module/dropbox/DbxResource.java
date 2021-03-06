package org.onedatashare.server.module.dropbox;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.files.*;
import org.onedatashare.server.model.core.*;
import org.onedatashare.server.model.error.NotFound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class DbxResource extends Resource<DbxSession, DbxResource> {

  DbxResource(DbxSession session, String path) {
    super(session, path);
  }

  @Override
  public Mono<DbxResource> select(String name) {
    return session.select(name);
  }

  public Flux<String> list() {
    return initialize().flux().flatMap(resource -> {
      ListFolderResult listing = null;
      try {
        listing = session.client.files().listFolder(path.toString());
      } catch (DbxException e) {
        e.printStackTrace();
      }
      return Flux.fromIterable(listing.getEntries()).map(Metadata::getName);
    });
  }

  public Mono<DbxResource> mkdir() {
    return initialize().doOnSuccess(resource -> {
      try {
        resource.session.client.files().createFolderV2(path.toString());
      } catch (DbxException e) {
        e.printStackTrace();
      }
    });
  }

  public Mono<DbxResource> delete() {
    return initialize().map(resource -> {
      try {
        resource.session.client.files().deleteV2(path.toString());
      } catch (DbxException e) {
        e.printStackTrace();
      }
      return resource;
    });
  }

  public Mono<Stat> stat() {
    return initialize().map(DbxResource::onStat);
  }

  public Stat onStat() {
    Stat stat = new Stat();
    ListFolderResult data = null;
    Metadata mData = null;
    try {
      if (path.equals("/")) {
        data = session.client.files().listFolder("");
      } else {
        try {
          String s = path;
          data = session.client.files().listFolder(path);
        } catch (ListFolderErrorException e) {
          mData = session.client.files().getMetadata(path);
        }
      }
      if (data == null && mData == null)
        throw new NotFound();
      if (data == null) {
        stat = mDataToStat(mData);
      } else {
        if (!data.getEntries().isEmpty()) {
          stat = mDataToStat(data.getEntries().iterator().next());
        }
        stat.dir = true;
        stat.file = false;
      }

      stat.name = path;

      if (stat.dir) {
        ListFolderResult lfr = null;
        if (stat.name.equals("/")) {
          lfr = session.client.files().listFolder("");
        } else {
          // If the metadata is a directory
          if (session.client.files().getMetadata(path) instanceof FolderMetadata) {
            // list the directory files
            lfr = session.client.files().listFolder(path);
          }
          // If the metadata is a file
          else if (session.client.files().getMetadata(path) instanceof FileMetadata) {
            // Return the metadata as a stat object
            stat = mDataToStat(session.client.files().getMetadata(path));
          }
        }
        List<Stat> sub = new LinkedList<>();
        for (Metadata child : lfr.getEntries())
          sub.add(mDataToStat(child));
        stat.setFiles(sub);
      }
    } catch (DbxException e) {
      e.printStackTrace();
    }
    return stat;
  }

  private Stat mDataToStat(Metadata data) {
    Stat stat = new Stat(data.getName());
    if (data instanceof FileMetadata) {
      FileMetadata file = (FileMetadata) data;
      stat.file = true;
      stat.size = file.getSize();
      stat.time = file.getClientModified().getTime() / 1000;
    }
    if (data instanceof FolderMetadata) {
      stat.dir = true;
    }
    return stat;
  }

  public DbxTap tap() {
    return new DbxTap();
  }

  public DbxDrain sink() {
    return new DbxDrain().start();
//    return slices.doOnNext(dbxDrain::drain).doFinally(s -> dbxDrain.finish());
  }

  class DbxTap implements Tap {
    DownloadBuilder downloadBuilder = session.client.files().downloadBuilder(path);
    final long size = stat().block().size;

    public Flux<Slice> tap(long sliceSize) {
      return Flux.generate(
              () -> 0L,
              (state, sink) -> {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                if (state + sliceSize < size) {
                  try {
                    downloadBuilder.range(state, sliceSize).start().download(outputStream);
                  } catch (DbxException | IOException e) {
                    e.printStackTrace();
                  }
                  sink.next(new Slice(outputStream.toByteArray()));
                  try {
                    outputStream.close();
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                } else {
                  try {
                    downloadBuilder.range(state, size - state).start().download(outputStream);
                  } catch (DbxException | IOException e) {
                    e.printStackTrace();
                  }
                  sink.next(new Slice(outputStream.toByteArray()));
                  sink.complete();
                }
                return state + sliceSize;
              });
    }
  }

  class DbxDrain implements Drain {
    final long CHUNKED_UPLOAD_CHUNK_SIZE = 1L << 20; // 1MiB
    long uploaded = 0L;
    InputStream in = new ByteArrayInputStream(new byte[]{});
    String sessionId;
    UploadSessionCursor cursor;

    public DbxDrain start() {
      try {
        sessionId = session.client.files().uploadSessionStart()
                .uploadAndFinish(in, 0L)
                .getSessionId();
        cursor = new UploadSessionCursor(sessionId, uploaded);
      } catch (Exception e) {
        e.printStackTrace();
      }
      return this;
    }

    public void drain(Slice slice) {
      InputStream sliceInputStream = new ByteArrayInputStream(slice.asBytes());
      try {
        session.client.files().uploadSessionAppendV2(cursor)
                .uploadAndFinish(sliceInputStream, slice.length());
      } catch (DbxException | IOException e) {
        e.printStackTrace();
      }
      uploaded += slice.length();
      cursor = new UploadSessionCursor(sessionId, uploaded);
    }

    public void finish() {
      CommitInfo commitInfo = CommitInfo.newBuilder(path)
              .withMode(WriteMode.ADD)
              .withClientModified(new Date())
              .build();
      try {
        FileMetadata metadata = session.client.files().uploadSessionFinish(cursor, commitInfo)
                .uploadAndFinish(in, 0L);
      } catch (DbxException | IOException e) {
        e.printStackTrace();
      }
    }
  }
}
