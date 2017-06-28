/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicecomb.serviceregistry.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import io.servicecomb.serviceregistry.api.registry.Microservice;
import io.servicecomb.serviceregistry.api.registry.MicroserviceInstance;
import io.servicecomb.serviceregistry.api.response.HeartbeatResponse;
import io.servicecomb.serviceregistry.client.ServiceRegistryClient;

public class MicroserviceInstanceHeartbeatTask extends AbstractTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicroserviceInstanceHeartbeatTask.class);

    private MicroserviceInstance microserviceInstance;

    // when instance register to service center success, start heartbeat
    private boolean instanceRegistered;

    private HeartbeatResult heartbeatResult;

    public MicroserviceInstanceHeartbeatTask(EventBus eventBus, ServiceRegistryClient srClient,
            Microservice microservice) {
        super(eventBus, srClient, microservice);
        this.microserviceInstance = microservice.getIntance();
    }

    @Subscribe
    public void onRegisterInstance(MicroserviceInstanceRegisterTask task) {
        if (task.isRegistered() && isSameMicroservice(task.getMicroservice())) {
            instanceRegistered = true;
        }
    }

    public HeartbeatResult getHeartbeatResult() {
        return heartbeatResult;
    }

    // only got service center response, and result is not ok, means need to register instance again. 
    public boolean isNeedRegisterInstance() {
        return HeartbeatResult.INSTANCE_NOT_REGISTERED.equals(heartbeatResult);
    }

    @Override
    public void run() {
        if (!instanceRegistered) {
            return;
        }

        heartbeatResult = heartbeat();
        eventBus.post(this);
    }

    private HeartbeatResult heartbeat() {
        HeartbeatResponse response =
            srClient.heartbeat(microserviceInstance.getServiceId(), microserviceInstance.getInstanceId());
        if (response == null) {
            //            if (!needToWatch()) {
            //                // TODO:不需要watch，为何要抛异常？
            //                exception(new ClientException("could not connect to service center"));
            //            }

            return HeartbeatResult.DISCONNECTED;
        }

        if (!response.isOk()) {
            LOGGER.error("Update heartbeat to service center failed, microservice instance={}/{} does not exist",
                    microserviceInstance.getServiceId(),
                    microserviceInstance.getInstanceId());
            return HeartbeatResult.INSTANCE_NOT_REGISTERED;
        }

        return HeartbeatResult.SUCCESS;
    }
}