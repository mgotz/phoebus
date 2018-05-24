package org.phoebus.applications.alarm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.phoebus.applications.alarm.client.AlarmClient;
import org.phoebus.applications.alarm.client.AlarmClientListener;
import org.phoebus.applications.alarm.client.AlarmClientNode;
import org.phoebus.applications.alarm.model.AlarmTreeItem;
import org.phoebus.applications.alarm.model.xml.XmlModelReader;
import org.phoebus.applications.alarm.model.xml.XmlModelWriter;
import org.phoebus.framework.jobs.NamedThreadFactory;

public class AlarmConfigTool
{
	// Time the model must be stable for. Unit is seconds. Default is 4 seconds.
	private long time = 4;

	private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Timer"));
    private final CountDownLatch no_more_messages = new CountDownLatch(1);
    private final Runnable signal_no_more_messages = () -> no_more_messages.countDown();
    private final AtomicReference<ScheduledFuture<?>> timeout = new AtomicReference<>();

    void resetTimer()
    {
        final ScheduledFuture<?> previous = timeout.getAndSet(timer.schedule(signal_no_more_messages, time, TimeUnit.SECONDS));
        if (previous != null)
            previous.cancel(false);
    }

	// Prints help info about the program and then exits.
	private void help()
	{
		System.out.println("AlarmToolConfig help menu. Usage defined below.");
		System.out.println("\n\tThis program facilitates the importation and exportation of the Alarm System's model via XML files.\n");
		System.out.println("\tTo print this menu: java AlarmToolConfig --help\n");
		System.out.println("\tUsing '--export' the program will write the Alarm System's current model to an XML file.");
		System.out.println("\n\tThe 'wait_time' argument refers to the amount of time the model must have been stable before it will be written to file.\n");
		System.out.println("\tTo export model to a file:  java AlarmToolConfig --export output_filename wait_time");
		System.out.println("\tTo export model to console: java AlarmToolConfig --export stdout wait_time\n");
		System.out.println("\tUsing '--import' the program will read a user supplied XML file and import the model contained therein to the Alarm System server.");
		System.out.println("\n\tTo import model from a file: java AlarmToolConfig --import input_filename");

		System.exit(0);
	}

	// Sets the timeout member variable.
	private void setTimeout(final long new_time)
	{
		time = new_time;
	}

	// Export an alarm system model to an xml file.
	private void exportModel(String filename) throws Exception
	{

		final AlarmClient client = new AlarmClient(AlarmDemoSettings.SERVERS, AlarmDemoSettings.ROOT);
        client.start();

        System.out.printf("Writing file after model is stable for %d seconds:\n", time);

        System.out.println("Monitoring changes...");

        AlarmClientListener updateListener = new AlarmClientListener()
        {
            @Override
            public void itemAdded(final AlarmTreeItem<?> item)
            {
            	// Reset the timer when receiving update
                resetTimer();
            }

            @Override
            public void itemRemoved(final AlarmTreeItem<?> item)
            {
            	// Reset the timer when receiving update
                resetTimer();
            }

            @Override
            public void itemUpdated(final AlarmTreeItem<?> item)
            {
            	//NOP
            }
        };

        client.addListener(updateListener);

        if (! no_more_messages.await(30, TimeUnit.SECONDS))
            throw new Exception("I give up waiting for updates to subside");

        System.out.printf("Received no more updates for %d seconds, I think I have a stable configuration\n", time);

        // Shutdown the client to stop the model from being changed again.
        client.removeListener(updateListener);

        final AtomicInteger updates = new AtomicInteger();

        updateListener = new AlarmClientListener()
        {
        	@Override
            public void itemAdded(final AlarmTreeItem<?> item)
            {
        		updates.incrementAndGet();
            }

            @Override
            public void itemRemoved(final AlarmTreeItem<?> item)
            {
        		updates.incrementAndGet();
            }

            @Override
            public void itemUpdated(final AlarmTreeItem<?> item)
            {
            	//NOP
            }
        };
        client.addListener(updateListener);

        //Write the model.

        final File modelFile = new File(filename);
        final FileOutputStream fos = new FileOutputStream(modelFile);

        XmlModelWriter xmlWriter = null;

        // Write to stdout or to file.
        if (0 == filename.compareTo("stdout"))
        {
        	xmlWriter = new XmlModelWriter(System.out);
        }
        else
        {
        	xmlWriter = new XmlModelWriter(fos);
        }

        xmlWriter.getModelXML(client.getRoot());

        System.out.println("\nModel written to file: " + filename);
        System.out.printf("%d updates were recieved while writing model to file.\n", updates.get());

        client.shutdown();
	}

	// Import an alarm system model from an xml file.
	private void importModel(final String filename) throws FileNotFoundException
	{
		final File file = new File(filename);
		final FileInputStream fileInputStream = new FileInputStream(file);

		final XmlModelReader xmlModelReader = new XmlModelReader();


		try
		{
			xmlModelReader.load(fileInputStream);
		} catch (final Exception e)
		{
			e.printStackTrace();
		}


		// Connect to the server.
		final AlarmClient client = new AlarmClient(AlarmDemoSettings.SERVERS, AlarmDemoSettings.ROOT);
        client.start();

        // Delete the old model.
        final AlarmClientNode root = client.getRoot();
        client.removeComponent(root);
	}

	// Constructor. Handles parsing of command lines and execution of command line options.
	private AlarmConfigTool(String[] args)
	{

		int wait_time = 0;
		for (int i = 0; i < args.length; i++)
		{
			if (0 == args[i].compareTo("--help"))
			{
				help();
			}
			if (0 == args[i].compareTo("--export"))
			{
				i++;
				if (i >= args.length)
				{
					System.out.println("ERROR: '--export' must be accompanied by an output file name and a wait time. Use --help for program usage info.");
					System.exit(1);
				}

				final String filename = args[i];
				i++;
				if (i >= args.length)
				{
					System.out.println("'ERROR: --export' must be accompanied by an output file name and a wait time. Use --help for program usage info.");
					System.exit(1);
				}

				try
				{
					wait_time = Integer.parseInt(args[i]);
				}
				catch (final NumberFormatException e)
				{
					System.out.println("ERROR: Wait time must be an integer value. Use --help for program usage info.");
					System.exit(1);
				}

				setTimeout(wait_time);

				try
				{
					exportModel(filename);
				}
				catch (final Exception e)
				{
					e.printStackTrace();
				}
			}
			else if (0 == args[i].compareTo("--import"))
			{
				i++;
				if (i >= args.length)
				{
					System.out.println("ERROR: '--import' must be accompanied by an input file name. Use --help for program usage info.");
					System.exit(1);
				}

				final String filename = args[i];

				try
				{
					importModel(filename);
				} catch (final FileNotFoundException e)
				{
					System.out.println("Input file: \"" + filename + "\" not found.");
					System.exit(1);
				}
			}
			else
			{
				System.out.printf("ERROR: Unrecognized command line option: \"%s\". Use --help for program usage info.", args[i]);
				System.exit(1);
			}
		}
	}

	public static void main(String[] args)
	{
		@SuppressWarnings("unused")
		final AlarmConfigTool act = new AlarmConfigTool(args);
	}

}
