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

package se.transientink.annostatemachine.processor;

import com.jayway.annostatemachine.ConnectionRef;
import com.jayway.annostatemachine.OnEnterRef;
import com.jayway.annostatemachine.OnExitRef;
import com.jayway.annostatemachine.SignalRef;
import com.jayway.annostatemachine.StateRef;
import com.jayway.annostatemachine.annotations.StateMachine;
import com.squareup.javawriter.JavaWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * The model that is used as input to the code generation component.
 */
class Model {

    private ArrayList<SignalRef> mSignals = new ArrayList<>();
    private HashMap<String, ArrayList<ConnectionRef>> mLocalSignalTransitions = new HashMap<>();
    private HashMap<String, ArrayList<ConnectionRef>> mLocalAnySignalSpies = new HashMap<>();
    private HashMap<String, ArrayList<ConnectionRef>> mLocalSignalSpies = new HashMap<>();
    private HashMap<String, ArrayList<ConnectionRef>> mLocalAnySignalTransitions = new HashMap<>();
    private ArrayList<ConnectionRef> mGlobalSignalSpies = new ArrayList<>();
    private ArrayList<ConnectionRef> mGlobalAnySignalSpies = new ArrayList<>();
    private ArrayList<ConnectionRef> mGlobalSignalTransitions = new ArrayList<>();
    private ArrayList<ConnectionRef> mGlobalAnySignalTransitions = new ArrayList<>();

    private ArrayList<StateRef> mStates = new ArrayList<>();

    // Aggregated info
    private HashMap<String, HashMap<String, ArrayList<ConnectionRef>>> mLocalSignalTransitionsPerSignalPerState = new HashMap<>();
    private HashMap<String, HashMap<String, ArrayList<ConnectionRef>>> mLocalSignalSpiesPerSignalPerState = new HashMap<>();
    private HashMap<String, ArrayList<ConnectionRef>> mGlobalSignalSpiesPerSignal = new HashMap<>();
    private HashMap<String, ArrayList<ConnectionRef>> mGlobalSignalTransitionsPerSignal = new HashMap<>();

    private String mSignalsEnumName;
    private String mSourceQualifiedName;
    private String mTargetPackage;
    private String mTargetClassName;
    private String mTargetClassQualifiedName;
    private String mSourceClassName;

    private StateMachine.DispatchMode mDispatchMode = StateMachine.DispatchMode.CALLING_THREAD;
    private int mDispatchQueueId = StateMachine.ID_GLOBAL_SHARED_QUEUE;
    private HashMap<String, OnExitRef> mOnExitCallbacks = new HashMap<>();
    private HashMap<String, OnEnterRef> mOnEnterCallbacks = new HashMap<>();
    private HashMap<String, ArrayList<ConnectionRef>> mAutoConnections = new HashMap<>();

    String getStatesEnumName() {
        return mStatesEnumName;
    }

    String getSignalsEnumName() {
        return mSignalsEnumName;
    }

    private String mStatesEnumName;

    private boolean mHasMainThreadConnections = false;

    void add(SignalRef signal) {
        mSignals.add(signal);
    }

    void add(ConnectionRef connection) throws IllegalArgumentException {

        mHasMainThreadConnections = mHasMainThreadConnections || connection.getRunOnMainThread();

        boolean hasWildcardFrom = ConnectionRef.WILDCARD.equals(connection.getFrom());
        boolean hasWildcardTo = ConnectionRef.WILDCARD.equals(connection.getTo());
        boolean hasWildcardSignal = ConnectionRef.WILDCARD.equals(connection.getSignalsAsString());
        boolean isAutoSignal = ConnectionRef.AUTO.equals(connection.getSignalsAsString());

        if (isAutoSignal) {
            if (hasWildcardFrom || hasWildcardTo) {
                throw new IllegalArgumentException("An auto signal must specify a from state and a to state " + connection);
            }
            addAutoConnection(connection);
        } else if (hasWildcardFrom) {
            // Global
            if (hasWildcardTo) {
                // Eavesdrop
                if (hasWildcardSignal) {
                    // Any signal
                    addGlobalAnySignalSpy(connection);
                } else {
                    // Specific signal
                    addGlobalSignalSpy(connection);
                }
            } else {
                // Normal
                if (hasWildcardSignal) {
                    // Any signal
                    addGlobalAnySignalTransition(connection);
                } else {
                    // Specific signal
                    addGlobalSignalTransition(connection);
                }
            }
        } else {
            // Local
            if (hasWildcardTo) {
                // Eavesdrop
                if (hasWildcardSignal) {
                    // Any signal
                    addLocalAnySignalSpy(connection);
                } else {
                    // Specific signal
                    addLocalSignalSpy(connection);
                }
            } else {
                // Normal
                if (hasWildcardSignal) {
                    // Any signal
                    addLocalAnySignalTransition(connection);
                } else {
                    // Specific signal
                    addLocalSignalTransition(connection);
                }
            }
        }
    }

    private void addAutoConnection(ConnectionRef connection) {
        ArrayList<ConnectionRef> connections = mAutoConnections.get(connection.getFrom());
        if (connections == null) {
            connections = new ArrayList<>();
        }
        connections.add(connection);
        mAutoConnections.put(connection.getFrom(), connections);
    }

    private void addLocalAnySignalTransition(ConnectionRef connection) {
        ArrayList<ConnectionRef> connections = mLocalAnySignalTransitions.get(connection.getFrom());
        if (connections == null) {
            connections = new ArrayList<>();
        }
        connections.add(connection);
        mLocalAnySignalTransitions.put(connection.getFrom(), connections);
    }

    private void addLocalSignalSpy(ConnectionRef connection) {
        ArrayList<ConnectionRef> connections = mLocalSignalSpies.get(connection.getFrom());
        if (connections == null) {
            connections = new ArrayList<>();
        }
        connections.add(connection);
        mLocalSignalSpies.put(connection.getFrom(), connections);
    }

    private void addLocalAnySignalSpy(ConnectionRef connection) {
        ArrayList<ConnectionRef> connections = mLocalAnySignalSpies.get(connection.getFrom());
        if (connections == null) {
            connections = new ArrayList<>();
        }
        connections.add(connection);
        mLocalAnySignalSpies.put(connection.getFrom(), connections);
    }

    private void addGlobalSignalTransition(ConnectionRef connection) {
        mGlobalSignalTransitions.add(connection);
    }

    private void addGlobalAnySignalTransition(ConnectionRef connection) {
        mGlobalAnySignalTransitions.add(connection);
    }

    private void addGlobalAnySignalSpy(ConnectionRef connection) {
        mGlobalAnySignalSpies.add(connection);
    }

    private void addGlobalSignalSpy(ConnectionRef connection) {
        mGlobalSignalSpies.add(connection);
    }

    private void addLocalSignalTransition(ConnectionRef connection) {
        ArrayList<ConnectionRef> connectionsForFromState = mLocalSignalTransitions.get(connection.getFrom());
        if (connectionsForFromState == null) {
            connectionsForFromState = new ArrayList<>();
        }
        connectionsForFromState.add(connection);
        mLocalSignalTransitions.put(connection.getFrom(), connectionsForFromState);
    }

    void add(StateRef state) {
        mStates.add(state);
    }

    void describeContents(JavaWriter javaWriter) throws IOException {
        javaWriter.emitSingleLineComment("--- States ---");
        for (StateRef stateRef : mStates) {
            javaWriter.emitSingleLineComment(" " + stateRef);
        }

        javaWriter.emitEmptyLine();
        javaWriter.emitSingleLineComment("--- Signals ---");
        for (SignalRef signalRef : mSignals) {
            javaWriter.emitSingleLineComment(" " + signalRef);
        }

        javaWriter.emitEmptyLine();
        javaWriter.emitSingleLineComment("--- Connections ---");

        if (mGlobalSignalTransitions != null && mGlobalSignalTransitions.size() > 0) {
            javaWriter.emitSingleLineComment("");
            javaWriter.emitSingleLineComment(" Global signal transitions: ");
            for (ConnectionRef transition : mGlobalSignalTransitions) {
                javaWriter.emitSingleLineComment("  " + transition);
            }
        }

        if (mGlobalAnySignalTransitions != null && mGlobalAnySignalTransitions.size() > 0) {
            javaWriter.emitSingleLineComment("");
            javaWriter.emitSingleLineComment(" Global any signal transitions: ");
            for (ConnectionRef transition : mGlobalAnySignalTransitions) {
                javaWriter.emitSingleLineComment("  " + transition);
            }
        }

        if (mGlobalSignalSpies != null && mGlobalSignalSpies.size() > 0) {
            javaWriter.emitSingleLineComment("");
            javaWriter.emitSingleLineComment(" Global signal spies: ");
            for (ConnectionRef transition : mGlobalSignalSpies) {
                javaWriter.emitSingleLineComment("  " + transition);
            }
        }

        if (mGlobalAnySignalSpies != null && mGlobalAnySignalSpies.size() > 0) {
            javaWriter.emitSingleLineComment("");
            javaWriter.emitSingleLineComment(" Global any signal spies: ");
            for (ConnectionRef transition : mGlobalAnySignalSpies) {
                javaWriter.emitSingleLineComment("  " + transition);
            }
        }

        List<ConnectionRef> localSignalTransitions;
        List<ConnectionRef> localAnySignalTransitions;
        List<ConnectionRef> localSignalSpies;
        List<ConnectionRef> localAnySignalSpies;

        for (StateRef state : mStates) {
            javaWriter.emitSingleLineComment("");
            javaWriter.emitSingleLineComment(" State: " + state.getName());

            localSignalTransitions = mLocalSignalTransitions.get(state.getName());
            if (localSignalTransitions != null && localSignalTransitions.size() > 0) {
                javaWriter.emitSingleLineComment("  Local signal transitions:");
                for (ConnectionRef transition : mLocalSignalTransitions.get(state.getName())) {
                    javaWriter.emitSingleLineComment("    " + transition);
                }
                javaWriter.emitSingleLineComment("");
            }

            List<ConnectionRef> autoConnections = mAutoConnections.get(state.getName());
            if (autoConnections != null) {
                for (ConnectionRef autoConnection : autoConnections) {
                    javaWriter.emitSingleLineComment("    " + autoConnection);
                }
                javaWriter.emitSingleLineComment("");
            }

            localAnySignalTransitions = mLocalAnySignalTransitions.get(state.getName());
            if (localAnySignalTransitions != null && localAnySignalTransitions.size() > 0) {
                javaWriter.emitSingleLineComment("  Local any signal transitions:");
                for (ConnectionRef transition : mLocalAnySignalTransitions.get(state.getName())) {
                    javaWriter.emitSingleLineComment("    " + transition);
                }
                javaWriter.emitSingleLineComment("");
            }

            localSignalSpies = mLocalSignalSpies.get(state.getName());
            if (localSignalSpies != null && localSignalSpies.size() > 0) {
                javaWriter.emitSingleLineComment("  Local signal spies:");
                for (ConnectionRef spy : mLocalSignalSpies.get(state.getName())) {
                    javaWriter.emitSingleLineComment("    " + spy);
                }
                javaWriter.emitSingleLineComment("");
            }

            localAnySignalSpies = mLocalAnySignalSpies.get(state.getName());
            if (localAnySignalSpies != null && localAnySignalSpies.size() > 0) {
                javaWriter.emitSingleLineComment("  Local any signal spies:");
                for (ConnectionRef spy : mLocalAnySignalSpies.get(state.getName())) {
                    javaWriter.emitSingleLineComment("    " + spy);
                }
                javaWriter.emitSingleLineComment("");
            }
        }

        javaWriter.emitSingleLineComment(ModelJsonExporter.getVisualizerJson(this));
    }

    void setSignalsEnum(TypeElement element) {
        mSignalsEnumName = element.getSimpleName().toString();
    }

    void setStatesEnum(TypeElement element) {
        mStatesEnumName = element.getSimpleName().toString();
    }

    void setDispatchMode(StateMachine.DispatchMode dispatchMode, int dispatchQueueId) {
        mDispatchMode = dispatchMode;
        mDispatchQueueId = dispatchQueueId;
    }

    void aggregateConnectionsPerSignal() {
        aggregateLocalSignalTransitionsPerSignalPerState();
        aggregateGlobalSpiesPerSignal();
        aggregateLocalSpiesPerSignal();
        aggregateGlobalSignalTransitionsPerSignal();
    }

    private void aggregateGlobalSignalTransitionsPerSignal() {
        for (ConnectionRef globalSpecificConnection : mGlobalSignalTransitions) {
            for (String signal : globalSpecificConnection.getSignals()) {
                ArrayList<ConnectionRef> connections = mGlobalSignalTransitionsPerSignal.get(signal);
                if (connections == null) {
                    connections = new ArrayList<>();
                }
                if (!connections.contains(globalSpecificConnection)) {
                    connections.add(globalSpecificConnection);
                }

                mGlobalSignalTransitionsPerSignal.put(signal, connections);
            }
        }
    }

    private void aggregateLocalSpiesPerSignal() {
        // Loop over all states and check their local spy connections
        for (Map.Entry<String, ArrayList<ConnectionRef>> spiesPerState : mLocalSignalSpies.entrySet()) {
            // Key is the state name
            // Value is the local signal spy for the state
            String stateName = spiesPerState.getKey();
            ArrayList<ConnectionRef> spiesForState = spiesPerState.getValue();
            HashMap<String, ArrayList<ConnectionRef>> spiesPerSignalInState = new HashMap<>();

            // Group the connections for the state by signal
            for (ConnectionRef connection : spiesForState) {
                for (String signal : connection.getSignals()) {
                    ArrayList<ConnectionRef> connections = spiesPerSignalInState.get(signal);
                    if (connections == null) {
                        connections = new ArrayList<>();
                    }
                    if (!connections.contains(connection)) {
                        connections.add(connection);
                    }
                    spiesPerSignalInState.put(signal, connections);
                }
            }
            mLocalSignalSpiesPerSignalPerState.put(stateName, spiesPerSignalInState);
        }
    }

    private void aggregateGlobalSpiesPerSignal() {
        for (ConnectionRef globalSpecificSpy : mGlobalSignalSpies) {
            for (String signal : globalSpecificSpy.getSignals()) {
                ArrayList<ConnectionRef> connections =
                    mGlobalSignalSpiesPerSignal.get(signal);
                if (connections == null) {
                    connections = new ArrayList<>();
                }
                if (!connections.contains(globalSpecificSpy)) {
                    connections.add(globalSpecificSpy);
                }
                mGlobalSignalSpiesPerSignal.put(signal, connections);
            }
        }
    }

    private void aggregateLocalSignalTransitionsPerSignalPerState() {
        // Loop over all states and check their local signal connections
        for (Map.Entry<String, ArrayList<ConnectionRef>> connectionsPerState : mLocalSignalTransitions.entrySet()) {
            // Key is the state name
            // Value is the local signal connections for the state
            String stateName = connectionsPerState.getKey();
            ArrayList<ConnectionRef> connectionsForState = connectionsPerState.getValue();
            HashMap<String, ArrayList<ConnectionRef>> connectionsPerSignalInState = new HashMap<>();

            // Group the connections for the state by signal
            for (ConnectionRef connection : connectionsForState) {
                for (String signal : connection.getSignals()) {
                    ArrayList<ConnectionRef> connections = connectionsPerSignalInState.get(signal);
                    if (connections == null) {
                        connections = new ArrayList<>();
                    }

                    if (!connections.contains(connection)) {
                        connections.add(connection);
                    }
                    connectionsPerSignalInState.put(signal, connections);
                }
            }
            mLocalSignalTransitionsPerSignalPerState.put(stateName, connectionsPerSignalInState);
        }
    }

    ArrayList<ConnectionRef> getGlobalAnySignalTransitions() {
        return mGlobalAnySignalTransitions;
    }

    HashMap<String, ArrayList<ConnectionRef>> getGlobalSignalTransitionsPerSignal() {
        return mGlobalSignalTransitionsPerSignal;
    }

    ArrayList<ConnectionRef> getGlobalAnySignalSpies() {
        return mGlobalAnySignalSpies;
    }

    HashMap<String, ArrayList<ConnectionRef>> getGlobalSignalSpiesPerSignal() {
        return mGlobalSignalSpiesPerSignal;
    }

    HashMap<String, ArrayList<ConnectionRef>> getLocalSignalTransitions() {
        return mLocalSignalTransitions;
    }

    boolean validateModel(String errorTag, Messager messager) {
        boolean isValid = true;

        isValid &= checkSignalsDefined(errorTag, messager);
        isValid &= checkStatesDefined(errorTag, messager);

        HashMap<String, StateRef> nameToStateMap = new HashMap<>();
        for (StateRef stateRef : mStates) {
            nameToStateMap.put(stateRef.getName(), stateRef);
        }

        for (Map.Entry<String, ArrayList<ConnectionRef>> entry : mLocalSignalTransitions.entrySet()) {
            if (entry.getValue() != null) {
                for (ConnectionRef connectionRef : entry.getValue()) {
                    if (!mStates.contains(nameToStateMap.get(connectionRef.getFrom()))) {
                        isValid = false;
                        messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Unknown FROM state "
                                + connectionRef.getFrom() + " used in connection " + connectionRef.getName() + ". Do you have a typo?");
                    }
                    if (!connectionRef.getTo().equals(ConnectionRef.WILDCARD) && !mStates.contains(nameToStateMap.get(connectionRef.getTo()))) {
                        isValid = false;
                        messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Unknown TO state "
                                + connectionRef.getTo() + " used in connection " + connectionRef.getName() + ". Do you have a typo?");
                    }
                    for (String signal : connectionRef.getSignals()) {
                        if (!mSignals.contains(new SignalRef(signal))) {
                            isValid = false;
                            messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Unknown SIGNAL "
                                + signal + " used in connection " + connectionRef.getName() + ". Do you have a typo?");
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, OnEnterRef> entry : mOnEnterCallbacks.entrySet()) {
            if (!mStates.contains(nameToStateMap.get(entry.getKey()))) {
                isValid = false;
                messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Unknown STATE "
                        + entry.getKey() + " used in OnEnter " + entry.getValue().getConnectionName() + ". Do you have a typo?");
            }
        }

        for (Map.Entry<String, OnEnterRef> entry : mOnEnterCallbacks.entrySet()) {
            if (!mStates.contains(nameToStateMap.get(entry.getKey()))) {
                isValid = false;
                messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Unknown STATE "
                        + entry.getKey() + " used in OnExit " + entry.getValue().getConnectionName() + ". Do you have a typo?");
            }
        }

        ArrayList<ConnectionRef> allAutoConnections = new ArrayList<>();
        for (Map.Entry<String, ArrayList<ConnectionRef>> entry : mAutoConnections.entrySet()) {
            allAutoConnections.addAll(entry.getValue());
        }
        for (ConnectionRef autoConnection : allAutoConnections) {
            if (!mStates.contains(nameToStateMap.get(autoConnection.getFrom()))) {
                isValid = false;
                messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Unknown from STATE "
                        + autoConnection.getFrom() + " used in auto connection " + autoConnection.getName() + ". Do you have a typo?");
            }
            if (!mStates.contains(nameToStateMap.get(autoConnection.getTo()))) {
                isValid = false;
                messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Unknown to STATE "
                        + autoConnection.getTo() + " used in auto connection " + autoConnection.getName() + ". Do you have a typo?");
            }
        }
        return isValid;
    }

    private boolean checkStatesDefined(String errorTag, Messager messager) {
        if (mSignalsEnumName == null || mSignalsEnumName.length() == 0) {
            messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Couldn't find an enum with the annotation @Signals");
            return false;
        }
        return true;
    }

    private boolean checkSignalsDefined(String errorTag, Messager messager) {
        if (mSignalsEnumName == null || mSignalsEnumName.length() == 0) {
            messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, errorTag + " - Couldn't find an enum with the annotation @Signals");
            return false;
        }
        return true;
    }

    ArrayList<StateRef> getStates() {
        return mStates;
    }

    ArrayList<ConnectionRef> getAnySignalTransitionsForState(StateRef stateRef) {
        return mLocalAnySignalTransitions.get(stateRef.getName());
    }

    HashMap<String, ArrayList<ConnectionRef>> getLocalSignalTransitionsPerSignalForState(StateRef stateRef) {
        return mLocalSignalTransitionsPerSignalPerState.get(stateRef.getName());
    }

    ArrayList<ConnectionRef> getLocalAnySignalSpiesForState(StateRef stateRef) {
        return mLocalAnySignalSpies.get(stateRef.getName());
    }

    HashMap<String, ArrayList<ConnectionRef>> getLocalSignalSpiesPerSignalForState(StateRef stateRef) {
        return mLocalSignalSpiesPerSignalPerState.get(stateRef.getName());
    }

    /**
     * Set the qualified name (package, class name, inner class name...) to generate a state machine
     * from.
     *
     * @param sourceClassQualifiedName The qualified name of the state machine declaration class.
     */
    void setSource(String sourceClassQualifiedName, String sourceClassName) {
        mSourceQualifiedName = sourceClassQualifiedName;
        mSourceClassName = sourceClassName;
    }

    /**
     * Set the target class information. This is the generated state machine implementation class.
     */
    void setTarget(String targetPackage, String targetClassName, String targetClassQualifiedName) {
        mTargetPackage = targetPackage;
        mTargetClassName = targetClassName;
        mTargetClassQualifiedName = targetClassQualifiedName;
    }

    String getTargetClassQualifiedName() {
        return mTargetClassQualifiedName;
    }

    String getTargetPackage() {
        return mTargetPackage;
    }

    String getSourceQualifiedName() {
        return mSourceQualifiedName;
    }

    String getTargetClassName() {
        return mTargetClassName;
    }

    String getSourceClassName() {
        return mSourceClassName;
    }

    StateMachine.DispatchMode getDispatchMode() {
        return mDispatchMode;
    }

    public int getDispatchQueueId() {
        return mDispatchQueueId;
    }

    public boolean hasMainThreadConnections() {
        return mHasMainThreadConnections;
    }

    public HashMap<String, OnExitRef> getOnExitCallbacks() {
        return mOnExitCallbacks;
    }

    public HashMap<String, OnEnterRef> getOnEnterCallbacks() {
        return mOnEnterCallbacks;
    }

    public void add(OnExitRef onExitRef) throws IllegalArgumentException {
        mHasMainThreadConnections = mHasMainThreadConnections || onExitRef.getRunOnMainThread();
        if (mOnExitCallbacks.containsKey(onExitRef.getState())) {
            throw new IllegalArgumentException("OnExit connection already added for state "
                    + onExitRef.getState() + " - duplicate: " + onExitRef.getConnectionName());
        }
        mOnExitCallbacks.put(onExitRef.getState(), onExitRef);
    }

    public void add(OnEnterRef onEnterRef) throws IllegalArgumentException {
        mHasMainThreadConnections = mHasMainThreadConnections || onEnterRef.getRunOnMainThread();
        if (mOnEnterCallbacks.containsKey(onEnterRef.getState())) {
            throw new IllegalArgumentException("OnEnter connection already added for state "
                    + onEnterRef.getState() + " - duplicate: " + onEnterRef.getConnectionName());
        }
        mOnEnterCallbacks.put(onEnterRef.getState(), onEnterRef);
    }

    public HashMap<String, ArrayList<ConnectionRef>> getAutoConnections() {
        return mAutoConnections;
    }
}
