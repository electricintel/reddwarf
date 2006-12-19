package com.sun.sgs.test.impl.service.session;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import junit.framework.Test;
import junit.framework.TestCase;

public class TestClientSessionServiceImpl extends TestCase {
    /** The name of the DataServiceImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestClientSessionServiceImpl.db";

    /** Properties for the session service. */
    private static Properties serviceProps = createProperties(
	"com.sun.sgs.appName", "TestClientSessionServiceImpl",
	"com.sun.sgs.app.port", "8306");

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory",
	dbDirectory,
	"com.sun.sgs.appName", "TestClientSessionServiceImpl",
	DataServiceImplClassName + ".debugCheckInterval", "1");
    
    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	System.err.println("Deleting database directory");
	deleteDirectory(dbDirectory);
    }

    /** A per-test database directory, or null if not created. */
    private String directory;
    
    private DummyTransactionProxy txnProxy;

    private DummyComponentRegistry registry;

    private DummyTransaction txn;

    private DataServiceImpl dataService;
    
    private ChannelServiceImpl channelService;

    private ClientSessionServiceImpl sessionService;

    private DummyTaskService taskService;

    private DummyTaskScheduler taskScheduler;

    private DummyIdentityManager identityManager;

    /** True if test passes. */
    private boolean passed;

    /** Constructs a test instance. */
    public TestClientSessionServiceImpl(String name) {
	super(name);
    }

    /** Creates and configures the session service. */
    protected void setUp() throws Exception {
	passed = false;
	System.err.println("Testcase: " + getName());
	txnProxy = new DummyTransactionProxy();
	createTransaction();
	registry = new DummyComponentRegistry();
	dataService = createDataService(registry);
	dataService.configure(registry, txnProxy);
	registry.setComponent(DataService.class, dataService);
	registry.registerAppContext();
	taskService = createTaskService();
	registry.setComponent(TaskService.class, taskService);
	taskScheduler = createTaskScheduler();
	registry.setComponent(TaskScheduler.class, taskScheduler);
	identityManager = createIdentityManager();
	registry.setComponent(IdentityManager.class, identityManager);
	sessionService = createSessionService();
	sessionService.configure(registry, txnProxy);
	registry.setComponent(ClientSessionService.class, sessionService);
	txn.commit();
	createTransaction();
	channelService = createChannelService();
	channelService.configure(registry, txnProxy);
	txn.commit();
	createTransaction();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
	sessionService.shutdown();
	if (txn != null) {
	    try {
		txn.abort();
	    } catch (IllegalStateException e) {
	    }
	    txn = null;
	}
    }
 
    /* -- Test constructor -- */

    public void testConstructorNullArg() {
	try {
	    new ClientSessionServiceImpl(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	try {
	    new ClientSessionServiceImpl(new Properties());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    /* -- other methods -- */

    /** Deletes the specified directory, if it exists. */
    static void deleteDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
    }

    /**
     * Creates a new transaction, and sets transaction proxy's
     * current transaction.
     */
    private DummyTransaction createTransaction() {
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }
    
    
    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }
 
    /**
     * Creates a new data service.  If the database directory does
     * not exist, one is created.
     */
    private DataServiceImpl createDataService(
	DummyComponentRegistry registry)
    {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps, registry);
    }

    /**
     * Creates a task service.
     */
    private DummyTaskService createTaskService() {
	return new DummyTaskService();
    }

    /**
     * Creates a task scheduler.
     */
    private DummyTaskScheduler createTaskScheduler() {
	return new DummyTaskScheduler();
    }

    /**
     * Creates an identity manager.
     */
    private DummyIdentityManager createIdentityManager() {
	return new DummyIdentityManager();
    }

    /** Creates a new channel service. */
    private static ChannelServiceImpl createChannelService() {
	return new ChannelServiceImpl(serviceProps);
    }

    /** Creates a new client session service. */
    private static ClientSessionServiceImpl createSessionService() {
	return new ClientSessionServiceImpl(serviceProps);
    }

    private static class DummyTaskService implements TaskService {

	public String getName() {
	    return toString();
	}

	public void configure(ComponentRegistry registry, TransactionProxy proxy) {
	}

	public PeriodicTaskHandle schedulePeriodicTask(
	    Task task, long delay, long period)
	{
	    throw new AssertionError("Not implemented");
	}

	public void scheduleTask(Task task) {
	    try {
		task.run();
	    } catch (Exception e) {
		System.err.println(
		    "DummyTaskService.scheduleTask exception: " + e);
		e.printStackTrace();
		throw (RuntimeException) (new RuntimeException()).initCause(e);
	    }
	}

	public void scheduleTask(Task task, long delay) {
	    scheduleTask(task);
	}
	
	public void scheduleNonDurableTask(KernelRunnable task) {
	    try {
		task.run();
	    } catch (Exception e) {
		System.err.println(
		    "DummyTaskService.scheduleNonDurableTask exception: " + e);
		e.printStackTrace();
		throw (RuntimeException) (new RuntimeException()).initCause(e);
	    }
	}
	
	public void scheduleNonDurableTask(KernelRunnable task, long delay) {
	    scheduleNonDurableTask(task);
	}
	public void scheduleNonDurableTask(KernelRunnable task,
					   Priority priority)
	{
	    scheduleNonDurableTask(task);
	}
    }

    private static class DummyTaskScheduler implements TaskScheduler {

	public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner) {
	    throw new AssertionError("Not implemented");
	}

	public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
					   Priority priority)
	{
	    throw new AssertionError("Not implemented");
	}

	public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
					   long startTime)
	{
	    throw new AssertionError("Not implemented");
	}

	public TaskReservation reserveTasks(Collection<? extends KernelRunnable>
					    tasks, TaskOwner owner)
	{
	    throw new AssertionError("Not implemented");
	}

	public void scheduleTask(KernelRunnable task, TaskOwner owner) {
	    try {
		task.run();
	    } catch (Exception e) {
		System.err.println(
		   "DummyTaskScheduler.scheduleTask exception: " + e);
		e.printStackTrace();
		throw (RuntimeException) (new RuntimeException()).initCause(e);
	    }
	}

	public void scheduleTask(KernelRunnable task, TaskOwner owner,
				 Priority priority)
	{
	    scheduleTask(task, owner);
	}

	public void scheduleTask(KernelRunnable task, TaskOwner owner,
				 long startTime)
	{
	    scheduleTask(task, owner);
	}

	public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
							 TaskOwner owner,
							 long startTime,
							 long period)
	{
	    throw new AssertionError("Not implemented");
	}

	
    }

    private static class DummyIdentityManager implements IdentityManager {
	public Identity authenticateIdentity(IdentityCredentials credentials) {
	    return new DummyIdentity(credentials);
	}
    }

    private static class DummyIdentity implements Identity {

	private final String name;

	DummyIdentity(IdentityCredentials credentials) {
	    this.name = ((NamePasswordCredentials) credentials).getName();
	}
	
	public String getName() {
	    return name;
	}

	public void notifyLoggedIn() {}

	public void notifyLoggedOut() {}
    }
}
