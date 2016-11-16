package iot.tempsensor;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.hirt.w1.Sensor;
import se.hirt.w1.Sensors;

public class TemperatureSensor implements ConfigurableComponent, CloudClientListener{

    private static final Logger s_logger = LoggerFactory.getLogger(TemperatureSensor.class);
    private static final String APP_ID = "TemperatureSensor";
        
    private static final String   PUBLISH_RATE_PROP_NAME   = "publish.rate";
	private static final String   PUBLISH_TOPIC_PROP_NAME  = "publish.semanticTopic";
	private static final String   PUBLISH_QOS_PROP_NAME    = "publish.qos";
	private static final String   PUBLISH_RETAIN_PROP_NAME = "publish.retain";
	
	private CloudService                m_cloudService;
	private CloudClient      			m_cloudClient;
	
	private ScheduledExecutorService    m_worker;
	private ScheduledFuture<?>          m_handle;
	
	private Map<String, Object>         m_properties;
		
	public TemperatureSensor() 
	{
		super();
		m_worker = Executors.newSingleThreadScheduledExecutor();
	}
	

	public void setCloudService(CloudService cloudService) {
		m_cloudService = cloudService;
	}

	public void unsetCloudService(CloudService cloudService) {
			m_cloudService = null;
	}


	protected void activate(ComponentContext componentContext, Map<String,Object> properties){
        s_logger.info("Activating Temp Sensor...");
		
		m_properties = properties;
		for (String s : properties.keySet()) {
			s_logger.info("Activate - "+s+": "+properties.get(s));
		}
		
		// get the mqtt client for this application
		try  {
			
			// Acquire a Cloud Application Client for this Application 
			s_logger.info("Getting CloudClient for {}...", APP_ID);
			m_cloudClient = m_cloudService.newCloudClient(APP_ID);
			m_cloudClient.addCloudClientListener(this);
			
			// Don't subscribe because these are handled by the default 
			// subscriptions and we don't want to get messages twice			
			doUpdate(false);
		}
		catch (Exception e) {
			s_logger.error("Error during component activation", e);
			throw new ComponentException(e);
		}
		s_logger.info("Activating Temp Sensor... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
    	s_logger.debug("Deactivating Temp Sensor...");

		// shutting down the worker and cleaning up the properties
		m_worker.shutdown();
		
		// Releasing the CloudApplicationClient
		s_logger.info("Releasing CloudApplicationClient for {}...", APP_ID);
		m_cloudClient.release();

		s_logger.debug("Deactivating Temp Sensor... Done.");
    }

    public void updated(Map<String,Object> properties)
	{
		s_logger.info("Updated Temp Sensor...");

		// store the properties received
		m_properties = properties;
		for (String s : properties.keySet()) {
			s_logger.info("Update - "+s+": "+properties.get(s));
		}
		
		// try to kick off a new job
		doUpdate(true);
		s_logger.info("Updated Temp Sensor... Done.");
	}
    
    /**
	 * Called after a new set of properties has been configured on the service
	 */
	private void doUpdate(boolean onUpdate) 
	{
		// cancel a current worker handle if one if active
		if (m_handle != null) {
			m_handle.cancel(true);
		}
		
		if (!m_properties.containsKey(PUBLISH_RATE_PROP_NAME)) {
			s_logger.info("Update Heater - Ignore as properties do not contain TEMP_INITIAL_PROP_NAME and PUBLISH_RATE_PROP_NAME.");
			return;
		}
		
		// schedule a new worker based on the properties of the service
		int pubrate = (Integer) m_properties.get(PUBLISH_RATE_PROP_NAME);
		m_handle = m_worker.scheduleAtFixedRate(new Runnable() {		
			@Override
			public void run() {
				Thread.currentThread().setName(getClass().getSimpleName());
				doPublish();
			}
		}, 0, pubrate, TimeUnit.SECONDS);
	}
	
	/**
	 * Called at the configured rate to publish the next temperature measurement.
	 */
	private void doPublish() 
	{				
		// fetch the publishing configuration from the publishing properties
		String  topic  = (String) m_properties.get(PUBLISH_TOPIC_PROP_NAME);
		Integer qos    = (Integer) m_properties.get(PUBLISH_QOS_PROP_NAME);
		Boolean retain = (Boolean) m_properties.get(PUBLISH_RETAIN_PROP_NAME);
		
		s_logger.info("topic: "+topic+", qos: "+qos+", retain: "+retain);
		
		String  payload = "";
		
		try {
			Set<Sensor> sensors = Sensors.getSensors();
			for (Sensor sensor : sensors) {
		       payload += String.format("%s(%s):%3.2f%s", sensor.getPhysicalQuantity(), 
		                                     sensor.getID(), sensor.getValue(), sensor.getUnitString());
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		if (payload.length() != 0) {
		    byte[] payloadBytes = payload.getBytes();
		   // Publish the message
		   try {
			  m_cloudClient.publish(topic, payloadBytes, qos, retain,1);
			  s_logger.info("Published to {} message: {}", topic, payloadBytes);
		   } 
		   catch (Exception e) {
			  s_logger.error("Cannot publish topic: "+topic, e);
		   }
		}
		
	}


	@Override
	public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onConnectionLost() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onConnectionEstablished() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onMessageConfirmed(int messageId, String appTopic) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onMessagePublished(int messageId, String appTopic) {
		// TODO Auto-generated method stub
		
	}
}
