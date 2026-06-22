package com.example.pythonembed;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Pool-aware {@link InvocationHandler} that acquires a
 * {@link PythonEmbedPool.PooledInstance} from the pool for each
 * method invocation, delegates the Python call, and releases the
 * instance back to the pool.
 *
 * <p>This ensures proxy calls benefit from pool auto-scaling,
 * health checks, and instance isolation.
 *
 * <p>Package-private - instantiated only by
 * {@link PythonEmbedPool#proxy}.
 */
class PythonPoolProxy implements InvocationHandler {

    private final PythonEmbedPool pool;
    private final int refId;

    PythonPoolProxy(PythonEmbedPool pool, int refId) {
        this.pool = pool;
        this.refId = refId;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        PythonEmbedPool.PooledInstance pi = null;
        try {
            pi = pool.acquireInstance();
            PythonEmbed embed = pi.embed;
            PythonProxy handler = new PythonProxy(
                    embed.protocol, embed.writer, refId,
                    embed.options.timeoutMs());
            return handler.invokePython(method, args);
        } catch (IOException e) {
            throw new PythonExecutionException("Pool acquire failed: " + e.getMessage(), e);
        } finally {
            if (pi != null) {
                pool.releaseInstance(pi);
            }
        }
    }
}
