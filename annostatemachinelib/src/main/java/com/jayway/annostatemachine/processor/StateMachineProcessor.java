package com.jayway.annostatemachine.processor;


import com.jayway.annostatemachine.ConnectionRef;
import com.jayway.annostatemachine.Constants;
import com.jayway.annostatemachine.SignalPayload;
import com.jayway.annostatemachine.SignalRef;
import com.jayway.annostatemachine.StateRef;
import com.jayway.annostatemachine.annotations.Connection;
import com.jayway.annostatemachine.annotations.Signals;
import com.jayway.annostatemachine.annotations.StateMachine;
import com.jayway.annostatemachine.annotations.States;
import com.squareup.javawriter.JavaWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("com.jayway.annostatemachine.annotations.StateMachine")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class StateMachineProcessor extends AbstractProcessor {

    private static final String TAG = StateMachineProcessor.class.getSimpleName();
    private static final String NEWLINE = "\n\n";
    private static final String GENERATED_FILE_SUFFIX = "Impl";

    private Model mModel = new Model();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(StateMachine.class)) {
            if (element.getKind().isClass()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        "Statemachine found: " + element.getSimpleName());
                generateStateMachine(element, roundEnv);
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Non class using " + StateMachine.class.getSimpleName() + " annotation");
            }
        }
        return true;
    }

    private void generateStateMachine(Element element, RoundEnvironment roundEnv) {
        String packageOfStateMachineSource = ((TypeElement) element).getQualifiedName().toString();

        String generatedPackage = Constants.libPackageName + ".generated";
        String generatedClassName = element.getSimpleName() + GENERATED_FILE_SUFFIX;
        String generatedClassFullPath = generatedPackage + "." + generatedClassName;

        JavaFileObject source = null;
        try {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Generating state machine implementation for " + packageOfStateMachineSource);
            source = processingEnv.getFiler().createSourceFile(generatedClassFullPath);
            try (Writer writer = source.openWriter(); JavaWriter javaWriter = new JavaWriter(writer)) {

                StringBuilder sb = new StringBuilder();

                generateMetadata(element, writer, javaWriter);

                javaWriter.emitPackage(generatedPackage);
                javaWriter.emitImports(packageOfStateMachineSource, SignalPayload.class.getCanonicalName());
                javaWriter.emitStaticImports(packageOfStateMachineSource + ".*");
                javaWriter.emitEmptyLine();
                javaWriter.beginType(generatedClassName, "class", EnumSet.of(Modifier.PUBLIC), element.getSimpleName().toString());

                mModel.describeContents(javaWriter);

                generateFields(javaWriter);
                generateInitMethod(javaWriter);

                generateSignalDispatcher(javaWriter);

                generateSignalHandlersForStates(javaWriter);

                generateSendMethod(javaWriter);
                generateSwitchStateMethod(javaWriter);

                // End class
                javaWriter.emitEmptyLine();
                javaWriter.endType();

                writer.close();
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Couldn't create generated state machine class: " + generatedClassName);
            e.printStackTrace();
        }
    }

    private void generateSendMethod(JavaWriter javaWriter) throws IOException {
        javaWriter.emitEmptyLine();
        javaWriter.beginMethod("void", "send", EnumSet.of(Modifier.PUBLIC), mModel.getSignalsEnumName(), "signal", "SignalPayload", "payload");
        javaWriter.emitStatement(mModel.getStatesEnumName() + " nextState = dispatchSignal(signal, payload)");
        javaWriter.beginControlFlow("if (nextState != null)");
        javaWriter.emitStatement("switchState(nextState)");
        javaWriter.endControlFlow();
        javaWriter.endMethod();
    }

    private void generateSwitchStateMethod(JavaWriter javaWriter) throws IOException {
        javaWriter.emitEmptyLine();
        javaWriter.beginMethod("void", "switchState", EnumSet.of(Modifier.PRIVATE), mModel.getStatesEnumName(), "nextState");
        javaWriter.emitStatement("System.out.println(\"Going from state \" + mCurrentState + \" to \" + nextState)");
        javaWriter.emitStatement("mCurrentState = nextState");
        javaWriter.endMethod();
    }

    private void generateSignalHandlersForStates(JavaWriter javaWriter) throws IOException {
        for (StateRef stateRef : mModel.mStates) {
            generateSignalHandler(stateRef, javaWriter);
        }
    }

    private void generateSignalHandler(StateRef stateRef, JavaWriter javaWriter) throws IOException {
        ArrayList<ConnectionRef> connectionsForState = mModel.mStateToConnectionsMap.get(stateRef.getName().toLowerCase());
        javaWriter.emitEmptyLine();
        javaWriter.beginMethod(mModel.getStatesEnumName(), "handleSignalIn" + camelCase(stateRef.getName()), EnumSet.of(Modifier.PRIVATE), mModel.getSignalsEnumName(), "signal", "SignalPayload", "payload");
        if (connectionsForState != null) {
            for (ConnectionRef connection : connectionsForState) {
                javaWriter.beginControlFlow("if (signal.equals(" + mModel.getSignalsEnumName() + "." + connection.getSignal() + "))");
                javaWriter.emitStatement("if (%s(payload)) return %s", connection.getName(), mModel.getStatesEnumName() + "." + connection.getTo());
                javaWriter.endControlFlow();
            }
        }
        javaWriter.emitStatement("return null");
        javaWriter.endMethod();
    }

    private void generateFields(JavaWriter javaWriter) throws IOException {
        javaWriter.emitField(mModel.getStatesEnumName(), "mCurrentState", EnumSet.of(Modifier.PRIVATE));
    }

    private void generateInitMethod(JavaWriter javaWriter) throws IOException {
        javaWriter.emitEmptyLine();
        javaWriter.beginMethod("void", "init", EnumSet.of(Modifier.PUBLIC), mModel.getStatesEnumName(), "startingState");
        javaWriter.emitStatement("mCurrentState = startingState"); // Force states and signals as enums
        javaWriter.endMethod();
    }

    private void generateSignalDispatcher(JavaWriter javaWriter) throws IOException {
        javaWriter.emitEmptyLine();
        javaWriter.beginMethod(mModel.getStatesEnumName(), "dispatchSignal", EnumSet.of(Modifier.PRIVATE), mModel.getSignalsEnumName(), "sig", "SignalPayload", "payload");

        javaWriter.beginControlFlow("switch (mCurrentState)");

        for (StateRef state : mModel.mStates) {
            javaWriter.emitStatement("case %s: return handleSignalIn%s(sig, payload)",
                    state.getName(), camelCase(state.getName()));
        }

        javaWriter.endControlFlow();

        javaWriter.emitStatement("return null");

        javaWriter.endMethod();
    }

    private void generateMetadata(Element element, Writer writer, JavaWriter javaWriter) throws IOException {
        javaWriter.emitEmptyLine();
        for (Element enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement.getAnnotation(States.class) != null) {
                collectStates(enclosedElement);
            } else if (enclosedElement.getAnnotation(Signals.class) != null) {
                collectSignals(enclosedElement);
            } else if (enclosedElement.getAnnotation(Connection.class) != null) {
                collectConnection(enclosedElement);
            }
        }
    }

    private void collectStates(Element element) {
        if (!(element.getKind().equals(ElementKind.ENUM))) {
            // States annotation on something other than an enum
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Non enum " + element.getSimpleName() + " of type " + element.getKind() + " using annotation " + States.class.getSimpleName());
            return;
        }

        mModel.setStatesEnum((TypeElement)element);
        List<? extends Element> values = element.getEnclosedElements();
        for (Element valueElement : values) {
            if (valueElement.getKind().equals(ElementKind.ENUM_CONSTANT)) {
                StateRef stateRef = new StateRef(valueElement.getSimpleName().toString());
                mModel.add(stateRef);
            }
        }
    }

    private void collectSignals(Element element) {
        if (!(element.getKind().equals(ElementKind.ENUM))) {
            // Signal annotation on something other than a enum
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Non enum " + element.getSimpleName() + " of type " + element.getKind() + " using annotation " + Signals.class.getSimpleName());
            return;
        }
        mModel.setSignalsEnum((TypeElement)element);
        List<? extends Element> values = element.getEnclosedElements();
        for (Element valueElement : values) {
            if (valueElement.getKind() == ElementKind.ENUM_CONSTANT) {
                SignalRef signalRef = new SignalRef(valueElement.getSimpleName().toString());
                mModel.add(signalRef);
            }
        }
    }

    private void collectConnection(Element element) {
        if (!(element.getKind() == ElementKind.METHOD)) {
            // Connection annotation on something other than a method
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Non method " + element.getSimpleName() + " using annotation " + Connection.class.getSimpleName());
            return;
        }

        String connectionName = element.getSimpleName().toString();
        Connection annotation = element.getAnnotation(Connection.class);

        ConnectionRef connectionRef = new ConnectionRef(connectionName, annotation.from(), annotation.to(), annotation.signal());
        mModel.add(connectionRef);
    }

    private static class Model {

        private ArrayList<SignalRef> mSignals = new ArrayList<>();
        private HashMap<String, ArrayList<ConnectionRef>> mStateToConnectionsMap = new HashMap<>();
        private ArrayList<StateRef> mStates = new ArrayList<>();

        private String mSignalsEnumClassQualifiedName;
        private String mStatesEnumClassQualifiedName;
        private String mSignalsEnumName;

        public String getStatesEnumName() {
            return mStatesEnumName;
        }

        public String getSignalsEnumQualifiedName() {
            return mSignalsEnumClassQualifiedName;
        }

        public String getStatesEnumQualifiedName() {
            return mStatesEnumClassQualifiedName;
        }

        public String getSignalsEnumName() {
            return mSignalsEnumName;
        }

        private String mStatesEnumName;

        public void add(SignalRef signal) {
            mSignals.add(signal);
        }

        public void add(ConnectionRef connection) {
            ArrayList<ConnectionRef> connectionsForFromState = mStateToConnectionsMap.get(connection.getFrom().toLowerCase());
            if (connectionsForFromState == null) {
                connectionsForFromState = new ArrayList<>();
            }
            connectionsForFromState.add(connection);
            mStateToConnectionsMap.put(connection.getFrom().toLowerCase(), connectionsForFromState);
        }

        public void add(StateRef state) {
            mStates.add(state);
        }

        public void describeContents(JavaWriter javaWriter) throws IOException {
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
            for (Map.Entry<String, ArrayList<ConnectionRef>> connectionEntry : mStateToConnectionsMap.entrySet()) {
                javaWriter.emitSingleLineComment("");
                javaWriter.emitSingleLineComment(" State: " + connectionEntry.getKey());
                for (ConnectionRef connection : connectionEntry.getValue()) {
                    javaWriter.emitSingleLineComment("   " + connection);
                }
            }
        }

        public void setSignalsEnum(TypeElement element) {
            mSignalsEnumClassQualifiedName = element.getQualifiedName().toString();
            mSignalsEnumName = element.getSimpleName().toString();
        }

        public void setStatesEnum(TypeElement element) {
            mStatesEnumClassQualifiedName = element.getQualifiedName().toString();
            mStatesEnumName = element.getSimpleName().toString();
        }
    }

    private static String camelCase(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase();
    }
}