/**
 * Copyright (C) 2020 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.twilio.actions;

import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Results({
        @Result(name="stops-index", location="stops/index", type="redirectAction", params={"From", "${phoneNumber}"}),
        @Result(name="search-index", location="search/index", type="redirectAction", params={"From", "${phoneNumber}"})
})
public class ContactAction extends TwilioSupport {

    private static final long serialVersionUID = 1L;
    private static Logger _log = LoggerFactory.getLogger(ContactAction.class);

    @Override
    public String execute() throws Exception {
        _log.debug("in ContactAction with input=" + getInput());

        if (getInput() == null) {
            if (System.getProperty("ivr.customerContact") != null) {
                addMessage(System.getProperty("ivr.customerContact"));
            } else {
                addMessage("For further help, please contact customer service.");
            }
            return INPUT;
        } else {
            _log.debug("Contact: input: " + getInput());
            if ("0".equals(getInput())) {
                clearNextAction();
                return "help";
            } else {
                return "";
            }
        }
    }
}