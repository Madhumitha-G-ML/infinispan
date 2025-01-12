package org.infinispan.jboss.marshalling.commons;

import java.io.IOException;

import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.reflect.SerializableClassRegistry;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.jboss.marshalling.river.RiverUnmarshaller;

/**
 * An extended {@link RiverUnmarshaller} that allows to track lifecycle of
 * unmarshaller so that pools can be notified when not in use any more.
 *
 * @author Galder Zamarreño
 * @since 5.1
 * @deprecated since 11.0. To be removed in 14.0 ISPN-11947.
 */
@Deprecated(forRemoval=true)
public class ExtendedRiverUnmarshaller extends RiverUnmarshaller {

   private RiverCloseListener listener;

   protected ExtendedRiverUnmarshaller(RiverMarshallerFactory factory,
         SerializableClassRegistry registry, MarshallingConfiguration cfg) {
      super(factory, registry, cfg);
   }

   void setCloseListener(RiverCloseListener closeListener) {
      this.listener = closeListener;
   }

   @Override
   public void finish() throws IOException {
      super.finish();
      if (listener != null) {
         listener.closeUnmarshaller();
      }
   }

   /**
    * Returns number unread buffered bytes.
    */
   public int getUnreadBufferedCount() {
      return limit - position;
   }

}
