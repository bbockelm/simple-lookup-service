package net.es.lookup.service;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.es.lookup.bootstrap.ScanLSJob;
import net.es.lookup.client.Subscriber;
import net.es.lookup.common.exception.internal.DatabaseException;
import net.es.lookup.database.MongoDBMaintenanceJob;
import net.es.lookup.database.ServiceDAOMongoDb;
import net.es.lookup.pubsub.client.ArchiveService;
import net.es.lookup.pubsub.amq.AMQueueManager;
import net.es.lookup.pubsub.amq.AMQueuePump;
import net.es.lookup.pubsub.client.ReplicationService;
import net.es.lookup.utils.*;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import static java.util.Arrays.asList;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;


public class Invoker {

    private static int port = 8080;
    private static LookupService lookupService = null;
    private static ServiceDAOMongoDb dao = null;
    private static String host = "localhost";
    private static LookupServiceConfigReader lcfg;
    private static String configPath="etc/";
    private static String lookupservicecfg = "lookupservice.yaml";
    private static String queuecfg = "queueservice.yaml";
    private static String subscribecfg = "subscriber.yaml";
    private static String bootstrapcfg = "bootstrap.yaml";
    private static String bootstrapoutput = "active-hosts.json";
    private static String logConfig = "./etc/log4j.properties";
    private static AMQueueManager amQueueManager = null;
    private static AMQueuePump amQueuePump = null;

    private static boolean queueservice = false;
    private static String mode;
    private static boolean bootstrapservice = false;

    public static String getConfigPath() {

        return configPath;
    }

    public static String getLookupservicecfg() {

        return lookupservicecfg;
    }

    public static String getQueuecfg() {

        return queuecfg;
    }

    public static String getSubscribecfg() {

        return subscribecfg;
    }

    public static String getBootstrapcfg() {

        return bootstrapcfg;
    }

    public static String getBootstrapoutput() {

        return bootstrapoutput;
    }

    public static String getLogConfig() {

        return logConfig;
    }

    /**
     * Main program to start the Lookup ServiceRecord
     *
     * @param args [-h, ?] for help
     *             [-p server-port
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        parseArgs(args);
        //set log config
        System.setProperty("log4j.configuration", "file:" + logConfig);


        LookupServiceConfigReader.init(configPath + lookupservicecfg);
        QueueServiceConfigReader.init(configPath+queuecfg);
        SubscriberConfigReader.init(configPath+subscribecfg);
        BootStrapConfigReader.init(configPath+bootstrapcfg);


        lcfg = LookupServiceConfigReader.getInstance();

        mode = lcfg.getMode();
        port = lcfg.getPort();
        host = lcfg.getHost();
        bootstrapservice = lcfg.isBootstrapserviceOn();

        int dbpruneInterval = lcfg.getPruneInterval();
        long prunethreshold = lcfg.getPruneThreshold();
        System.out.println("starting ServiceDAOMongoDb");

        try {

            Invoker.dao = new ServiceDAOMongoDb();

        } catch (DatabaseException e) {

            System.out.println("Error connecting to database; Please check if MongoDB is running");
            System.exit(1);

        }

        System.out.println("starting Lookup Service");
        // Create the REST service
        Invoker.lookupService = new LookupService(Invoker.host, Invoker.port);


        //Queue service
        QueueServiceConfigReader qcfg = QueueServiceConfigReader.getInstance();

        if (qcfg.getServiceState().equals(LookupServiceOptions.SERVICE_ON)) {
            queueservice = true;
            //starting queueservice
            Invoker.amQueueManager = AMQueueManager.getInstance();
            Invoker.amQueuePump = AMQueuePump.getInstance();
            Invoker.lookupService.setQueueurl(qcfg.getUrl());

        } else {
            queueservice = false;
        }

        Invoker.lookupService.setQueueServiceRequired(queueservice);

        // Start the service
        Invoker.lookupService.startService();

        if (mode.equalsIgnoreCase(LookupServiceOptions.MODE_REPLICATION)) {
            ReplicationService replicationService = new ReplicationService();
            replicationService.start();

        } else if (mode.equalsIgnoreCase(LookupServiceOptions.MODE_ARCHIVE)) {
            ArchiveService archiveService = new ArchiveService();
            archiveService.start();
        }
        //DB Pruning  and Bootstrap
        try {

            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();

            // define the job and tie it to  mongoJob class
            JobDetail job = newJob(MongoDBMaintenanceJob.class)
                    .withIdentity("mongoJob", "DBMaintenance")
                    .build();
            job.getJobDataMap().put(MongoDBMaintenanceJob.PRUNE_THRESHOLD, prunethreshold);

            // Trigger the job to run now, and then every dbpruneInterval seconds
            Trigger trigger = newTrigger().withIdentity("DBTrigger", "DBMaintenance")
                    .startNow()
                    .withSchedule(simpleSchedule()
                            .withIntervalInSeconds(dbpruneInterval)
                            .repeatForever())
                    .build();

            scheduler.scheduleJob(job, trigger);

            //bootstrap job
            Scheduler bootstrapScheduler = StdSchedulerFactory.getDefaultScheduler();
            bootstrapScheduler.start();
            if(bootstrapservice){
                JobDetail bootstrapJob = newJob(ScanLSJob.class)
                        .withIdentity("scanLS","bootstrap")
                        .build();

                Trigger bootstrapTrigger = newTrigger().withIdentity("scanLSTrigger","bootstrap")
                        .startNow()
                        .withSchedule(simpleSchedule()
                                .repeatForever()
                                .withIntervalInSeconds(180))
                        .build();

                bootstrapScheduler.scheduleJob(bootstrapJob,bootstrapTrigger);
            }

        } catch (SchedulerException se) {

            se.printStackTrace();

        }


        // Block forever
        Object blockMe = new Object();
        synchronized (blockMe) {
            blockMe.wait();

        }

    }


    public static void parseArgs(String args[]) throws java.io.IOException {

        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("h", "?"), "show help then exit");
        OptionSpec<String> PORT = parser.accepts("p", "server port").withRequiredArg().ofType(String.class);
        OptionSpec<String> HOST = parser.accepts("h", "host").withRequiredArg().ofType(String.class);
        OptionSpec<String> CONFIG = parser.accepts("c", "configPath").withRequiredArg().ofType(String.class);
        OptionSpec<String> LOGCONFIG = parser.accepts("l", "logConfig").withRequiredArg().ofType(String.class);
        OptionSet options = parser.parse(args);

        // check for help
        if (options.has("?")) {

            parser.printHelpOn(System.out);
            System.exit(0);

        }

        if (options.has(PORT)) {

            port = Integer.parseInt(options.valueOf(PORT));

        }

        if (options.has(HOST)) {

            host = options.valueOf(HOST);

        }

        if (options.has(CONFIG)) {
            System.out.println("Config files Path:"+ CONFIG);
            configPath = options.valueOf(CONFIG);

        }

        if (options.has(LOGCONFIG)) {

            logConfig = options.valueOf(LOGCONFIG);

        }

    }


}