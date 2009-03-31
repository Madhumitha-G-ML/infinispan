package org.horizon.distribution;

import org.horizon.remoting.transport.Address;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A consistent hash algorithm
 *
 * @author Manik Surtani
 * @since 4.0
 */
public interface ConsistentHash {

   List<Address> locate(Object key, int replCount);

   Map<Object, List<Address>> locate(Collection<Object> keys, int replCount);
   
}
