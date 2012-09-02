/*
Copyright 2012 Artem Stasuk

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.github.javaniotcpproxy;

import com.github.javaniotcpproxy.configuration.TcpProxyConfig;
import com.github.javaniotcpproxy.handler.TcpProxyHandler;
import com.github.javaniotcpproxy.handler.TcpProxyHandlerAcceptor;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TcpProxyConnector {

    private final static Logger LOGGER = Logger.getAnonymousLogger();

    private final TcpProxyConfig config;
    private final String name;

    private Queue<TcpProxyHandler> handlers;
    private Thread[] workers;

    public TcpProxyConnector(final TcpProxyConfig config) {
        this.config = config;
        name = "TcpProxyConnector to " + config.getRemoteHost() + ":" + config.getRemotePort()
                + " from " + config.getLocalPort();
    }

    /**
     * This method starts waiting incoming connections for proxy to remote host.
     * Method return control when all worker will be started, it isn't block.
     *
     * @param workerCount - count of workers
     * @throws IOException                   - if problem with opening selector
     * @throws IllegalArgumentException      - if count of worker < 1
     * @throws UnsupportedOperationException - if you try start already started connector
     */
    public void start(final int workerCount) throws IOException {
        if (workerCount < 1) throw new IllegalArgumentException("Count of workers should be more 1!");
        if (workers != null) throw new UnsupportedOperationException("Please shutdown connector!");

        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("Starting " + name + " with " + workerCount + " workers");

        handlers = new ConcurrentLinkedQueue<TcpProxyHandler>();
        handlers.add(new TcpProxyHandlerAcceptor(config, handlers));

        workers = new Thread[workerCount];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new TcpProxyWorker(handlers);
        }

        for (final Thread worker : workers) worker.start();

        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info(name + " started");
    }

    /**
     * Shutdown connector.
     * <p/>
     * This method wait when all resources will be closed.
     * You can call this method any time.
     * No problem and exceptions if you try to shutdown connector twice without start.
     */
    public void shutdown() {
        if (workers == null) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.info(name + " already been shutdown");
            return;
        }

        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("Starting shutdown " + name);

        for (final Thread worker : workers) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        workers = null;

        TcpProxyHandler handler;
        while ((handler = handlers.poll()) != null) handler.destroy();
        handlers = null;

        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info(name + " was shutdown");
    }

}
