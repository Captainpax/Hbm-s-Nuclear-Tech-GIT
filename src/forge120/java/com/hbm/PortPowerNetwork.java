package com.hbm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

/** One server-tick transfer across a connected HE network. Topology is supplied by the caller. */
public final class PortPowerNetwork {
    public enum Priority { LOWEST, LOW, NORMAL, HIGH, HIGHEST }

    @FunctionalInterface
    public interface TransferObserver {
        void transferred(PortPowerReservoir source, PortPowerReservoir destination, long amount);
    }

    public record Provider(PortPowerReservoir storage, long maxOutput) {
        public Provider {
            if (storage == null || maxOutput < 0) throw new IllegalArgumentException("Invalid HE provider");
        }
    }

    public record Receiver(PortPowerReservoir storage, long maxInput, Priority priority) {
        public Receiver {
            if (storage == null || maxInput < 0 || priority == null) {
                throw new IllegalArgumentException("Invalid HE receiver");
            }
        }
    }

    private PortPowerNetwork() {}

    /** Distributes higher priorities first, proportionally within a priority, with no HE created or lost. */
    public static long transfer(List<Provider> providers, List<Receiver> receivers) {
        return transfer(providers, receivers, (source, destination, amount) -> {});
    }

    /** Reports actual transfers so world networks can enforce aggregate per-tick machine rates. */
    public static long transfer(List<Provider> providers, List<Receiver> receivers, TransferObserver observer) {
        if (observer == null) throw new IllegalArgumentException("Missing HE transfer observer");
        rejectDuplicateStorages(providers, receivers);
        long[] outputLeft = new long[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            Provider provider = providers.get(i);
            outputLeft[i] = Math.min(provider.storage().power(), provider.maxOutput());
        }

        long moved = 0;
        for (int priority = Priority.HIGHEST.ordinal(); priority >= Priority.LOWEST.ordinal(); priority--) {
            List<Receiver> group = new ArrayList<>();
            List<Long> demand = new ArrayList<>();
            BigInteger demandTotal = BigInteger.ZERO;
            for (Receiver receiver : receivers) {
                if (receiver.priority().ordinal() != priority) continue;
                long wanted = Math.min(receiver.storage().capacity() - receiver.storage().power(),
                        receiver.maxInput());
                if (wanted <= 0) continue;
                group.add(receiver);
                demand.add(wanted);
                demandTotal = demandTotal.add(BigInteger.valueOf(wanted));
            }
            if (group.isEmpty()) continue;

            List<Long> supply = new ArrayList<>();
            BigInteger supplyTotal = BigInteger.ZERO;
            for (int i = 0; i < providers.size(); i++) {
                long available = Math.min(outputLeft[i], providers.get(i).storage().power());
                supply.add(available);
                supplyTotal = supplyTotal.add(BigInteger.valueOf(available));
            }
            long budget = supplyTotal.min(demandTotal).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            if (budget == 0) break;
            long[] share = proportionalShares(budget, demand, demandTotal);
            long[] providerShare = proportionalShares(budget, supply, supplyTotal);
            for (int i = 0; i < group.size(); i++) {
                long needed = share[i];
                PortPowerReservoir destination = group.get(i).storage();
                // The second pass only spends a provider's remaining output when a
                // proportional share could not be used, e.g. a buffer cannot feed itself.
                for (int pass = 0; pass < 2 && needed > 0; pass++) {
                    for (int sourceIndex = 0; sourceIndex < providers.size() && needed > 0; sourceIndex++) {
                        PortPowerReservoir source = providers.get(sourceIndex).storage();
                        if (source == destination) continue;
                        long limit = pass == 0 ? providerShare[sourceIndex] : outputLeft[sourceIndex];
                        long amount = Math.min(needed, limit);
                        amount = Math.min(amount, source.power());
                        amount = Math.min(amount, destination.capacity() - destination.power());
                        if (amount == 0) continue;
                        long extracted = source.extract(amount);
                        long accepted = destination.receive(extracted);
                        if (accepted != extracted) source.receive(extracted - accepted);
                        outputLeft[sourceIndex] -= accepted;
                        providerShare[sourceIndex] = Math.max(0, providerShare[sourceIndex] - accepted);
                        needed -= accepted;
                        if (accepted > 0) observer.transferred(source, destination, accepted);
                        moved = accepted > Long.MAX_VALUE - moved ? Long.MAX_VALUE : moved + accepted;
                    }
                }
            }
        }
        return moved;
    }

    private static long[] proportionalShares(long budget, List<Long> demand, BigInteger demandTotal) {
        long[] shares = new long[demand.size()];
        BigInteger[] remainders = new BigInteger[demand.size()];
        long assigned = 0;
        for (int i = 0; i < demand.size(); i++) {
            BigInteger[] part = BigInteger.valueOf(budget).multiply(BigInteger.valueOf(demand.get(i)))
                    .divideAndRemainder(demandTotal);
            shares[i] = part[0].longValueExact();
            remainders[i] = part[1];
            assigned += shares[i];
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < shares.length; i++) order.add(i);
        order.sort(Comparator.<Integer, BigInteger>comparing(i -> remainders[i]).reversed()
                .thenComparingInt(i -> i));
        for (long i = 0, left = budget - assigned; i < left; i++) shares[order.get((int) i)]++;
        return shares;
    }

    private static void rejectDuplicateStorages(List<Provider> providers, List<Receiver> receivers) {
        IdentityHashMap<PortPowerReservoir, Boolean> seen = new IdentityHashMap<>();
        for (Provider provider : providers) {
            if (seen.put(provider.storage(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate HE provider storage");
            }
        }
        seen.clear();
        for (Receiver receiver : receivers) {
            if (seen.put(receiver.storage(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate HE receiver storage");
            }
        }
    }
}
