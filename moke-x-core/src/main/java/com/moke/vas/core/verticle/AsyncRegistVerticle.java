package com.moke.vas.core.verticle;


import com.moke.vas.core.anno.AsyncServiceHandler;
import com.moke.vas.core.utils.ReflectionUtil;
import com.moke.vas.core.utils.SpringContextUtil;
import com.moke.vas.core.vertx.VertxUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Async service register to EventBus
 */
public class AsyncRegistVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncRegistVerticle.class);

    private String packageAddress;

    public AsyncRegistVerticle(String packageAddress) {
        Objects.requireNonNull(packageAddress, "given scan package address is empty");
        this.packageAddress = packageAddress;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Set<Class<?>> handlers = ReflectionUtil.getReflections(packageAddress).getTypesAnnotatedWith(AsyncServiceHandler.class);
        ServiceBinder binder = new ServiceBinder(VertxUtil.getVertxInstance());
        if (null != handlers && handlers.size() > 0) {
            List<Future> ftList = new ArrayList<>();
            handlers.forEach(asyncService -> {
                Future ft = Future.future();
                try {
                    Object asInstance = SpringContextUtil.getBean(asyncService);
                    Method getAddressMethod = asyncService.getMethod("getAddress");
                    String address = (String) getAddressMethod.invoke(asInstance);
                    Method getAsyncInterfaceClassMethod = asyncService.getMethod("getAsyncInterfaceClass");
                    Class clazz = (Class) getAsyncInterfaceClassMethod.invoke(asInstance);
                    binder.setAddress(address).register(clazz, asInstance).completionHandler(ft);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                ftList.add(ft);
            });
            CompositeFuture.all(ftList).setHandler(ar -> {
                if (ar.succeeded()) {
                    LOGGER.info("All async services registered");
                    startFuture.complete();
                } else {
                    LOGGER.error(ar.cause().getMessage());
                    startFuture.fail(ar.cause());
                }
            });
        }
    }
}
