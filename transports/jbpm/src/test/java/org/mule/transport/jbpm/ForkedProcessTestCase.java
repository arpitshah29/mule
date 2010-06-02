/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.jbpm;

import org.mule.api.MuleMessage;
import org.mule.module.client.MuleClient;

import org.jbpm.api.ProcessInstance;

/**
 * Tests the connector against jBPM with a simple process.
 */
public class ForkedProcessTestCase extends AbstractJbpmTestCase
{
    @Override
    protected String getConfigResources()
    {
        return "jbpm-functional-test.xml";
    }

    public void testForkedProcess() throws Exception 
    {
        MuleMessage response;
        ProcessInstance process;
        MuleClient client = new MuleClient(muleContext);
        try
        {
            // Create a new process.
            response = client.send("bpm://fork", "data", null);                      
            process = (ProcessInstance) response.getPayload();
            
            // The process should be waiting for asynchronous responses from both services
            String state = (String) bpms.getState(process);
            assertTrue(state.contains("waitForResponseA"));
            assertTrue(state.contains("waitForResponseB"));

            Thread.sleep(2000);

            // ServiceA is initially stopped, so we're still waiting for a response from ServiceA
            process = (ProcessInstance) bpms.lookupProcess(process.getId());
            assertEquals("waitForResponseA", bpms.getState(process));

            // Start ServiceA
            muleContext.getRegistry().lookupService("ServiceA").start();
            Thread.sleep(2000);
                        
            // The process should have ended.
            process = (ProcessInstance) bpms.lookupProcess(process.getId());
            assertTrue("Process should have ended, but is in state " + bpms.getState(process), 
            		bpms.hasEnded(process));
        }
        finally
        {
            client.dispose();
        }
    }
}
