package com.github.gosu.jsr223;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

/**
 * Created with IntelliJ IDEA.
 * User: rberlin
 * Date: 10/11/13
 * Time: 5:59 PM
 * To change this template use File | Settings | File Templates.
 */
class IOAdapters {
  public static OutputStream adaptOutput(final Writer writer) {
    return new OutputStream() {

      @Override
      public void write(int b) throws IOException {
        writer.write(b);
      }
    };
  }

  public static InputStream adaptInput(final Reader reader) {
    return new InputStream() {

      @Override
      public int read() throws IOException {
        return reader.read();
      }
    };
  }
}
