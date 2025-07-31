/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.authenticator.fido2.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.fido.metadata.MetadataBLOBPayloadEntry;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.UserIdentity;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockObjectFactory;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.authenticator.fido2.dao.FIDO2DeviceStoreDAO;
import org.wso2.carbon.identity.application.authenticator.fido2.dto.FIDO2CredentialRegistration;
import org.wso2.carbon.identity.application.authenticator.fido2.exception.FIDO2AuthenticatorServerException;
import org.wso2.carbon.identity.application.authenticator.fido2.util.FIDO2ExecutorConstants;
import org.wso2.carbon.identity.application.authenticator.fido2.util.FIDOUtil;
import org.wso2.carbon.identity.flow.execution.engine.Constants;
import org.wso2.carbon.identity.flow.execution.engine.model.FlowExecutionContext;
import org.wso2.carbon.identity.flow.execution.engine.model.FlowExecutionStep;
import org.wso2.carbon.identity.flow.execution.engine.model.FlowUser;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.verify;

/**
 * Unit tests for RegistrationFlowCompletionListener.
 */
@PrepareForTest({FIDOUtil.class, FIDO2DeviceStoreDAO.class})
public class RegistrationFlowCompletionListenerTest {

    private static final String USERNAME = "testuser";
    private static final String CONTEXT_IDENTIFIER = "test-flow-123";
    private static final String CREDENTIAL_ID = "test-credential-id";
    private static final String DISPLAY_NAME = "Test Device";
    private static final long SIGNATURE_COUNT = 1L;
    private static final String REGISTRATION_TIME_STRING = "2025-01-01T10:00:00Z";

    private RegistrationFlowCompletionListener listener;

    @Mock
    private FlowExecutionStep flowExecutionStep;

    @Mock
    private FlowExecutionContext flowExecutionContext;

    @Mock
    private FlowUser flowUser;

    @Mock
    private FIDO2DeviceStoreDAO deviceStoreDAO;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        listener = new RegistrationFlowCompletionListener();

        // Mock static classes
        mockStatic(FIDOUtil.class);
        mockStatic(FIDO2DeviceStoreDAO.class);

        // Setup basic mocks
        when(FIDO2DeviceStoreDAO.getInstance()).thenReturn(deviceStoreDAO);
        when(flowExecutionContext.getFlowUser()).thenReturn(flowUser);
        when(flowUser.getUsername()).thenReturn(USERNAME);
        when(flowExecutionContext.getContextIdentifier()).thenReturn(CONTEXT_IDENTIFIER);
    }

    @Test
    public void testGetExecutionOrderId() {
        Assert.assertEquals(listener.getExecutionOrderId(), 5);
    }

    @Test
    public void testGetDefaultOrderId() {
        Assert.assertEquals(listener.getDefaultOrderId(), 5);
    }

    @Test
    public void testIsEnabled() {
        Assert.assertTrue(listener.isEnabled());
    }

    @Test
    public void testDoPostExecuteWithRegistrationFlowAndCompleteStatus() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createTestCredentialRegistrationMap();
        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doNothing().when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    @Test
    public void testDoPostExecuteWithNonRegistrationFlow() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(false);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        // Should not call DAO when not registration flow
    }

    @Test
    public void testDoPostExecuteWithIncompleteStatus() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_INCOMPLETE);

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        // Should not call DAO when status is not complete
    }

    @Test
    public void testDoPostExecuteWithNullCredentialRegistration() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);
        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(null);

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        // Should not call DAO when credential registration is null
    }

    @Test
    public void testDoPostExecuteWithDAOException() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createTestCredentialRegistrationMap();
        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doThrow(new FIDO2AuthenticatorServerException("DAO error", new Exception("Test exception")))
                .when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result); // Should return true even when exception occurs
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    @Test
    public void testBuildFromMapWithCompleteData() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createTestCredentialRegistrationMap();
        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doNothing().when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    @Test
    public void testBuildFromMapWithMinimalData() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createMinimalCredentialRegistrationMap();
        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doNothing().when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    @Test
    public void testBuildFromMapWithNullOptionalFields() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createCredentialRegistrationMapWithNulls();
        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doNothing().when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    @Test
    public void testBuildFromMapWithoutRegistrationTime() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createTestCredentialRegistrationMap();
        credentialRegistrationMap.remove(FIDO2ExecutorConstants.RegistrationConstants.REGISTRATION_TIME);

        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doNothing().when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    @Test
    public void testBuildFromMapWithZeroSignatureCount() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createTestCredentialRegistrationMap();
        credentialRegistrationMap.put(FIDO2ExecutorConstants.RegistrationConstants.SIGNATURE_COUNT, null);

        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doNothing().when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    @Test
    public void testBuildFromMapWithUsernamelessSupported() throws Exception {
        // Setup
        when(FIDOUtil.isRegistrationFlow(flowExecutionContext)).thenReturn(true);
        when(flowExecutionStep.getFlowStatus()).thenReturn(Constants.STATUS_COMPLETE);

        Map<String, Object> credentialRegistrationMap = createTestCredentialRegistrationMap();
        credentialRegistrationMap.put(FIDO2ExecutorConstants.RegistrationConstants.IS_USERNAMELESS_SUPPORTED, true);

        when(flowExecutionContext.getProperty(FIDO2ExecutorConstants.CREDENTIAL_REGISTRATION))
                .thenReturn(credentialRegistrationMap);

        doNothing().when(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));

        // Execute
        boolean result = listener.doPostExecute(flowExecutionStep, flowExecutionContext);

        // Verify
        Assert.assertTrue(result);
        verify(deviceStoreDAO).addFIDO2RegistrationByUsername(eq(USERNAME), any(FIDO2CredentialRegistration.class));
    }

    /**
     * Helper method to create a complete credential registration map for testing.
     */
    private Map<String, Object> createTestCredentialRegistrationMap() {
        Map<String, Object> map = new HashMap<>();

        // Mock credential
        Map<String, Object> credentialMap = new HashMap<>();
        credentialMap.put("credentialId", CREDENTIAL_ID);
        credentialMap.put("userHandle", "test-user-handle");
        credentialMap.put("publicKeyCose", "test-public-key");
        credentialMap.put("signatureCount", SIGNATURE_COUNT);
        map.put(FIDO2ExecutorConstants.CREDENTIAL, credentialMap);

        // Mock user identity
        Map<String, Object> userIdentityMap = new HashMap<>();
        userIdentityMap.put("name", USERNAME);
        userIdentityMap.put("displayName", "Test User");
        userIdentityMap.put("id", "test-user-id");
        map.put(FIDO2ExecutorConstants.RegistrationConstants.USER_IDENTITY, userIdentityMap);

        // Other fields
        map.put(FIDO2ExecutorConstants.RegistrationConstants.CREDENTIAL_NICKNAME, "Test Nickname");
        map.put(FIDO2ExecutorConstants.RegistrationConstants.ATTESTATION_METADATA, null);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.SIGNATURE_COUNT, SIGNATURE_COUNT);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.DISPLAY_NAME, DISPLAY_NAME);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.IS_USERNAMELESS_SUPPORTED, false);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.REGISTRATION_TIME, REGISTRATION_TIME_STRING);

        return map;
    }

    /**
     * Helper method to create a minimal credential registration map for testing.
     */
    private Map<String, Object> createMinimalCredentialRegistrationMap() {
        Map<String, Object> map = new HashMap<>();

        // Mock minimal credential
        Map<String, Object> credentialMap = new HashMap<>();
        credentialMap.put("credentialId", CREDENTIAL_ID);
        credentialMap.put("userHandle", "test-user-handle");
        credentialMap.put("publicKeyCose", "test-public-key");
        credentialMap.put("signatureCount", 0L);
        map.put(FIDO2ExecutorConstants.CREDENTIAL, credentialMap);

        // Mock minimal user identity
        Map<String, Object> userIdentityMap = new HashMap<>();
        userIdentityMap.put("name", USERNAME);
        userIdentityMap.put("displayName", USERNAME);
        userIdentityMap.put("id", "test-user-id");
        map.put(FIDO2ExecutorConstants.RegistrationConstants.USER_IDENTITY, userIdentityMap);

        // Minimal required fields
        map.put(FIDO2ExecutorConstants.RegistrationConstants.SIGNATURE_COUNT, 0L);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.DISPLAY_NAME, USERNAME);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.IS_USERNAMELESS_SUPPORTED, false);

        return map;
    }

    /**
     * Helper method to create a credential registration map with null optional fields.
     */
    private Map<String, Object> createCredentialRegistrationMapWithNulls() {
        Map<String, Object> map = new HashMap<>();

        // Mock credential
        Map<String, Object> credentialMap = new HashMap<>();
        credentialMap.put("credentialId", CREDENTIAL_ID);
        credentialMap.put("userHandle", "test-user-handle");
        credentialMap.put("publicKeyCose", "test-public-key");
        credentialMap.put("signatureCount", SIGNATURE_COUNT);
        map.put(FIDO2ExecutorConstants.CREDENTIAL, credentialMap);

        // Mock user identity
        Map<String, Object> userIdentityMap = new HashMap<>();
        userIdentityMap.put("name", USERNAME);
        userIdentityMap.put("displayName", "Test User");
        userIdentityMap.put("id", "test-user-id");
        map.put(FIDO2ExecutorConstants.RegistrationConstants.USER_IDENTITY, userIdentityMap);

        // Set optional fields to null
        map.put(FIDO2ExecutorConstants.RegistrationConstants.CREDENTIAL_NICKNAME, null);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.ATTESTATION_METADATA, null);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.SIGNATURE_COUNT, SIGNATURE_COUNT);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.DISPLAY_NAME, null);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.IS_USERNAMELESS_SUPPORTED, false);
        map.put(FIDO2ExecutorConstants.RegistrationConstants.REGISTRATION_TIME, null);

        return map;
    }

    @ObjectFactory
    public IObjectFactory getObjectFactory() {
        return new PowerMockObjectFactory();
    }
}
