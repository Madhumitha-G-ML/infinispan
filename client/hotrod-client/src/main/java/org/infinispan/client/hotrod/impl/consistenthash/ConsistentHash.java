package org.infinispan.client.hotrod.impl.consistenthash;

import java.net.SocketAddress;
import java.util.Map;
import java.util.Set;

/**
 * Abstraction for the used consistent hash.
 *
 * @author Mircea.Markus@jboss.com
 * @since 4.1
 */
public interface ConsistentHash {

   @Deprecated(forRemoval=true)
   void init(Map<SocketAddress, Set<Integer>> servers2Hash, int numKeyOwners, int hashSpace);

   SocketAddress getServer(Object key);

   /**
    * Computes hash code of a given object, and then normalizes it to ensure a positive
    * value is always returned.
    * @param object to hash
    * @return a non-null, non-negative normalized hash code for a given object
    */
   int getNormalizedHash(Object object);

   Map<SocketAddress, Set<Integer>> getSegmentsByServer();

   default Map<SocketAddress, Set<Integer>> getPrimarySegmentsByServer() {
      return null;
   }
}
