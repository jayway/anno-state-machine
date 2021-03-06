/*
 * Copyright 2017 Jayway (http://www.jayway.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jayway.annostatemachine;

public class NullEventListener implements StateMachineEventListener {
    @Override
    public void onDispatchingSignal(Object currentState, Object signal) {

    }

    @Override
    public void onChangingState(Object currentState, Object nextState) {

    }

    @Override
    public void onThrowable(Throwable t) {

    }
}
