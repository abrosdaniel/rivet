package dev.abros.rivet.core.modules;

import java.util.*;

/** Dependency-ordered resource ownership. All calls belong to the owning game thread. */
public final class ModuleRuntime<C> {
    @FunctionalInterface public interface Action<C> { void run(C context) throws Exception; }
    @FunctionalInterface public interface Availability<C> { boolean test(C context) throws Exception; }
    public record Module<C>(String id, List<String> requires, Availability<C> available,
                            Action<C> start, Action<C> stop) {
        public Module {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Empty module id");
            requires = List.copyOf(requires);
            Objects.requireNonNull(available); Objects.requireNonNull(start); Objects.requireNonNull(stop);
        }
    }
    private final List<Module<C>> order;
    private final List<Module<C>> running = new ArrayList<>();
    private C context;
    private boolean started;

    public ModuleRuntime(List<Module<C>> modules) {
        Map<String, Module<C>> definitions = new LinkedHashMap<>();
        for (var module : modules)
            if (definitions.putIfAbsent(module.id(), module) != null)
                throw new IllegalArgumentException("Duplicate module: " + module.id());
        List<Module<C>> sorted = new ArrayList<>();
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (var module : modules) visit(module, definitions, visiting, visited, sorted);
        order = List.copyOf(sorted);
    }
    private static <C> void visit(Module<C> module, Map<String, Module<C>> definitions,
                                  Set<String> visiting, Set<String> visited, List<Module<C>> sorted) {
        if (visited.contains(module.id())) return;
        if (!visiting.add(module.id())) throw new IllegalArgumentException("Cyclic module dependency: " + module.id());
        for (String id : module.requires()) {
            var required = definitions.get(id);
            if (required == null) throw new IllegalArgumentException("Unknown dependency: " + id);
            visit(required, definitions, visiting, visited, sorted);
        }
        visiting.remove(module.id()); visited.add(module.id()); sorted.add(module);
    }
    public void start(C value) throws Exception {
        if (started) throw new IllegalStateException("Modules already started");
        context = Objects.requireNonNull(value); started = true;
        Set<String> active = new HashSet<>();
        try {
            for (var module : order) {
                if (!active.containsAll(module.requires()) || !module.available().test(context)) continue;
                // Include partially initialized resources in rollback.
                running.add(module);
                module.start().run(context);
                active.add(module.id());
            }
        } catch (Exception | Error failure) {
            try { stop(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    public Set<String> active() {
        Set<String> ids = new LinkedHashSet<>(); running.forEach(m -> ids.add(m.id()));
        return Collections.unmodifiableSet(ids);
    }
    public void stop() throws Exception {
        if (!started) return;
        Throwable failure = null;
        try {
            for (int i = running.size() - 1; i >= 0; i--) {
                try { running.get(i).stop().run(context); }
                catch (Exception | Error error) {
                    if (failure == null) failure = error; else failure.addSuppressed(error);
                }
            }
        } finally { running.clear(); context = null; started = false; }
        if (failure instanceof Error error) throw error;
        if (failure instanceof Exception error) throw error;
    }
}
