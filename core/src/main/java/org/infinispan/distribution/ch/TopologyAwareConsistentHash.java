package org.infinispan.distribution.ch;

import org.infinispan.remoting.transport.Address;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.lang.Math.min;

/**
 * Consistent hash that is aware of cluster topology.
 * Design described here: http://community.jboss.org/wiki/DesigningServerHinting.
 * <p>
 * Algorithm:
 * - place nodes on the hash wheel based address's hash code
 * - For selecting owner nodes:
 *       - pick the first one based on key's hash code
 *       - for subsequent nodes, walk clockwise and pick nodes that have a different site id
 *       - if not enough nodes found repeat walk again and pick nodes that have different site id and rack id
 *       - if not enough nodes found repeat walk again and pick nodes that have different site id, rack id and machine id
 *       - Ultimately cycle back to the first node selected, don't discard any nodes, regardless of machine id/rack
 * id/site id match.

 *
 * @author Mircea.Markus@jboss.com
 * @since 4.2
 */
public class TopologyAwareConsistentHash extends AbstractWheelConsistentHash {

   public List<Address> locate(Object key, int replCount) {
      Address owner = getOwner(key);
      int ownerCount = min(replCount, addresses.size());
      return getOwners(owner, ownerCount);
   }

   public List<Address> getStateProvidersOnLeave(Address leaver, int replCount) {
      Set<Address> result = new HashSet<Address>();

      //1. first get all the node that replicated on leaver
      for (Address address : addresses) {
         if (address.equals(leaver)) continue;
         if (getOwners(address, replCount).contains(leaver)) {
            result.add(address);
         }
      }

      //2. then get first leaver's backup
      List<Address> addressList = getOwners(leaver, replCount);
      if (addressList.size() > 1) {
         result.add(addressList.get(1));
      }
      return new ArrayList<Address>(result);
   }


   /**
    * In this situation are the same nodes providing state on join as the nodes that provide state on leave.
    */
   public List<Address> getStateProvidersOnJoin(Address joiner, int replCount) {
      return getStateProvidersOnLeave(joiner, replCount);
   }

   private List<Address> getOwners(Address address, int numOwners) {
      int ownerHash = getNormalizedHash(address);
      Collection<Address> beforeOnWheel = positions.headMap(ownerHash).values();
      Collection<Address> afterOnWheel = positions.tailMap(ownerHash).values();
      ArrayList<Address> processSequence = new ArrayList<Address>(afterOnWheel);
      processSequence.addAll(beforeOnWheel);
      List<Address> result = new ArrayList<Address>();
      result.add(processSequence.remove(0));
      int level = 0;
      while (result.size() < numOwners) {
         Iterator<Address> addrIt = processSequence.iterator();
         while (addrIt.hasNext()) {
            Address a = addrIt.next();
            switch (level) {
               case 0 : { //site level
                  if (!topologyInfo.isSameSite(address, a)) {
                     result.add(a);
                     addrIt.remove();
                  }
                  break;
               }
               case 1 : { //rack level
                  if (!topologyInfo.isSameRack(address, a)) {
                     result.add(a);
                     addrIt.remove();
                  }
                  break;
               }
               case 2 : { //machine level
                  if (!topologyInfo.isSameMachine(address, a)) {
                     result.add(a);
                     addrIt.remove();
                  }
                  break;
               }
               case 3 : { //just add them in sequence
                  result.add(a);
                  addrIt.remove();
                  break;
               }
            }
            if (result.size() == numOwners) break;
         }
         level++;
      }
      //assertion
      if (result.size() != numOwners) throw new AssertionError("This should not happen!");
      return result;
   }

   private Address getOwner(Object key) {
      int hash = getNormalizedHash(key);
      Integer ownerHash = positions.tailMap(hash).firstKey();
      return positions.get(ownerHash);
   }
}
