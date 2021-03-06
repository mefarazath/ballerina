/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.util.parser.antlr4;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringEscapeUtils;
import org.ballerinalang.model.NodeLocation;
import org.ballerinalang.model.builder.BLangModelBuilder;
import org.ballerinalang.model.types.SimpleTypeName;
import org.ballerinalang.util.parser.BallerinaListener;
import org.ballerinalang.util.parser.BallerinaParser;
import org.ballerinalang.util.parser.BallerinaParser.ActionDefinitionContext;
import org.ballerinalang.util.parser.BallerinaParser.AnnotationAttachmentContext;
import org.ballerinalang.util.parser.BallerinaParser.AnnotationAttributeArrayContext;
import org.ballerinalang.util.parser.BallerinaParser.AnnotationAttributeContext;
import org.ballerinalang.util.parser.BallerinaParser.AnnotationAttributeListContext;
import org.ballerinalang.util.parser.BallerinaParser.AnnotationAttributeValueContext;
import org.ballerinalang.util.parser.BallerinaParser.AnnotationBodyContext;
import org.ballerinalang.util.parser.BallerinaParser.AnnotationDefinitionContext;
import org.ballerinalang.util.parser.BallerinaParser.ArrayLiteralContext;
import org.ballerinalang.util.parser.BallerinaParser.ArrayLiteralExpressionContext;
import org.ballerinalang.util.parser.BallerinaParser.AttachmentPointContext;
import org.ballerinalang.util.parser.BallerinaParser.BuiltInReferenceTypeNameContext;
import org.ballerinalang.util.parser.BallerinaParser.BuiltInReferenceTypeTypeExpressionContext;
import org.ballerinalang.util.parser.BallerinaParser.CallableUnitBodyContext;
import org.ballerinalang.util.parser.BallerinaParser.CallableUnitSignatureContext;
import org.ballerinalang.util.parser.BallerinaParser.ContinueStatementContext;
import org.ballerinalang.util.parser.BallerinaParser.DefinitionContext;
import org.ballerinalang.util.parser.BallerinaParser.FieldDefinitionContext;
import org.ballerinalang.util.parser.BallerinaParser.MapStructKeyValueContext;
import org.ballerinalang.util.parser.BallerinaParser.MapStructLiteralContext;
import org.ballerinalang.util.parser.BallerinaParser.MapStructLiteralExpressionContext;
import org.ballerinalang.util.parser.BallerinaParser.NameReferenceContext;
import org.ballerinalang.util.parser.BallerinaParser.ReferenceTypeNameContext;
import org.ballerinalang.util.parser.BallerinaParser.SimpleLiteralContext;
import org.ballerinalang.util.parser.BallerinaParser.SimpleLiteralExpressionContext;
import org.ballerinalang.util.parser.BallerinaParser.StructBodyContext;
import org.ballerinalang.util.parser.BallerinaParser.TypeMapperSignatureContext;
import org.ballerinalang.util.parser.BallerinaParser.ValueTypeNameContext;
import org.ballerinalang.util.parser.BallerinaParser.ValueTypeTypeExpressionContext;
import org.ballerinalang.util.parser.BallerinaParser.XmlLocalNameContext;
import org.ballerinalang.util.parser.BallerinaParser.XmlNamespaceNameContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Build the Ballerina language model using the listener events from antlr4 parser.
 *
 * @see BLangModelBuilder
 * @since 0.8.0
 */
public class BLangAntlr4Listener implements BallerinaListener {
    protected static final String B_KEYWORD_PUBLIC = "public";
    protected static final String B_KEYWORD_NATIVE = "native";
    protected String fileName;
    protected String packageDirPath;
    protected String currentPkgName;
    protected BLangModelBuilder modelBuilder;

    // Types related attributes
    protected String typeName;
    // private String schemaID;

    protected boolean processingReturnParams = false;
    protected Stack<SimpleTypeName> typeNameStack = new Stack<>();
    protected Stack<BLangModelBuilder.NameReference> nameReferenceStack = new Stack<>();

    // Variable to keep whether worker creation has been started. This is used at BLangAntlr4Listener class
    // to create parameter when there is a named parameter
    protected boolean isWorkerStarted = false;
    protected boolean isTypeMapperStarted = false;
    protected boolean processingActionInvocationStmt = false;

    public BLangAntlr4Listener(BLangModelBuilder modelBuilder, Path sourceFilePath) {
        this.modelBuilder = modelBuilder;
        this.fileName = sourceFilePath.getFileName().toString();

        if (sourceFilePath.getNameCount() >= 2) {
            this.packageDirPath = sourceFilePath.subpath(0, sourceFilePath.getNameCount() - 1).toString();
        } else {
            this.packageDirPath = null;
        }
    }

    @Override
    public void enterCompilationUnit(BallerinaParser.CompilationUnitContext ctx) {
    }

    @Override
    public void exitCompilationUnit(BallerinaParser.CompilationUnitContext ctx) {
    }

    @Override
    public void enterPackageDeclaration(BallerinaParser.PackageDeclarationContext ctx) {
    }

    @Override
    public void exitPackageDeclaration(BallerinaParser.PackageDeclarationContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.addPackageDcl(ctx.packageName().getText());
    }

    @Override
    public void enterPackageName(BallerinaParser.PackageNameContext ctx) {
    }

    @Override
    public void exitPackageName(BallerinaParser.PackageNameContext ctx) {
    }

    @Override
    public void enterImportDeclaration(BallerinaParser.ImportDeclarationContext ctx) {
    }

    @Override
    public void exitImportDeclaration(BallerinaParser.ImportDeclarationContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String pkgPath = ctx.packageName().getText();
        String asPkgName = (ctx.Identifier() != null) ? ctx.Identifier().getText() : null;
        modelBuilder.addImportPackage(getCurrentLocation(ctx), pkgPath, asPkgName);
    }

    @Override
    public void enterDefinition(DefinitionContext ctx) {

    }

    @Override
    public void exitDefinition(DefinitionContext ctx) {

    }

    @Override
    public void enterServiceDefinition(BallerinaParser.ServiceDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startServiceDef(getCurrentLocation(ctx));
    }

    @Override
    public void exitServiceDefinition(BallerinaParser.ServiceDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String serviceName = ctx.Identifier().getText();
        modelBuilder.createService(serviceName);
    }

    @Override
    public void enterServiceBody(BallerinaParser.ServiceBodyContext ctx) {

    }

    @Override
    public void exitServiceBody(BallerinaParser.ServiceBodyContext ctx) {
    }

    @Override
    public void enterResourceDefinition(BallerinaParser.ResourceDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startResourceDef();
    }

    @Override
    public void exitResourceDefinition(BallerinaParser.ResourceDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String resourceName = ctx.Identifier().getText();
        int annotationCount = ctx.annotationAttachment() != null ? ctx.annotationAttachment().size() : 0;
        modelBuilder.addResource(getCurrentLocation(ctx), resourceName, annotationCount);

    }

    @Override
    public void enterCallableUnitBody(CallableUnitBodyContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startCallableUnitBody(getCurrentLocation(ctx));
    }

    @Override
    public void exitCallableUnitBody(CallableUnitBodyContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.endCallableUnitBody();
    }

    @Override
    public void enterFunctionDefinition(BallerinaParser.FunctionDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startFunctionDef(getCurrentLocation(ctx));
    }

    @Override
    public void exitFunctionDefinition(BallerinaParser.FunctionDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        boolean isNative = B_KEYWORD_NATIVE.equals(ctx.getChild(0).getText());
        String functionName = ctx.callableUnitSignature().Identifier().getText();
        modelBuilder.addFunction(functionName, isNative);
    }

    @Override
    public void enterCallableUnitSignature(CallableUnitSignatureContext ctx) {

    }

    @Override
    public void exitCallableUnitSignature(CallableUnitSignatureContext ctx) {

    }

    @Override
    public void enterConnectorDefinition(BallerinaParser.ConnectorDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startConnectorDef(getCurrentLocation(ctx));
    }

    @Override
    public void exitConnectorDefinition(BallerinaParser.ConnectorDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String connectorName = ctx.Identifier().getText();
        modelBuilder.createConnector(connectorName);
    }

    @Override
    public void enterConnectorBody(BallerinaParser.ConnectorBodyContext ctx) {
    }

    @Override
    public void exitConnectorBody(BallerinaParser.ConnectorBodyContext ctx) {
    }

    @Override
    public void enterActionDefinition(ActionDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startActionDef(getCurrentLocation(ctx));
    }

    @Override
    public void exitActionDefinition(ActionDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        boolean isNative = B_KEYWORD_NATIVE.equals(ctx.getChild(0).getText());
        String actionName = ctx.callableUnitSignature().Identifier().getText();
        int annotationCount = ctx.annotationAttachment() != null ? ctx.annotationAttachment().size() : 0;
        modelBuilder.addAction(actionName, isNative, annotationCount);
    }

    @Override
    public void enterStructDefinition(BallerinaParser.StructDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startStructDef(getCurrentLocation(ctx));
    }

    @Override
    public void exitStructDefinition(BallerinaParser.StructDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String structName = ctx.Identifier().getText();
        modelBuilder.addStructDef(structName);
    }

    @Override
    public void enterStructBody(StructBodyContext ctx) {

    }

    @Override
    public void exitStructBody(StructBodyContext ctx) {

    }

    @Override
    public void enterAnnotationDefinition(AnnotationDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startAnnotationDef(getCurrentLocation(ctx));
    }

    @Override
    public void exitAnnotationDefinition(AnnotationDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.addAnnotationtDef(getCurrentLocation(ctx), ctx.Identifier().getText());
    }

    @Override
    public void enterAttachmentPoint(AttachmentPointContext ctx) {

    }

    @Override
    public void exitAttachmentPoint(AttachmentPointContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.addAnnotationtAttachmentPoint(getCurrentLocation(ctx), ctx.getText());
    }

    @Override
    public void enterAnnotationBody(AnnotationBodyContext ctx) {

    }

    @Override
    public void exitAnnotationBody(AnnotationBodyContext ctx) {

    }

    @Override
    public void enterTypeMapperDefinition(BallerinaParser.TypeMapperDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startTypeMapperDef(getCurrentLocation(ctx));
    }

    @Override
    public void exitTypeMapperDefinition(BallerinaParser.TypeMapperDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        boolean isNative = B_KEYWORD_NATIVE.equals(ctx.getChild(0).getText());
        String typeMapperName = ctx.typeMapperSignature().Identifier().getText();
        SimpleTypeName returnTypeName = typeNameStack.pop();
        modelBuilder.addTypeMapper(getCurrentLocation(ctx), typeMapperName, returnTypeName, isNative);
    }

    @Override
    public void enterTypeMapperSignature(TypeMapperSignatureContext ctx) {

    }

    @Override
    public void exitTypeMapperSignature(TypeMapperSignatureContext ctx) {

    }

    @Override
    public void enterTypeMapperBody(BallerinaParser.TypeMapperBodyContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startCallableUnitBody(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitTypeMapperBody(BallerinaParser.TypeMapperBodyContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.endCallableUnitBody();
        }
    }

    @Override
    public void enterConstantDefinition(BallerinaParser.ConstantDefinitionContext ctx) {
    }

    @Override
    public void exitConstantDefinition(BallerinaParser.ConstantDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        SimpleTypeName typeName = typeNameStack.pop();
        String constName = ctx.Identifier().getText();

        modelBuilder.addConstantDef(getCurrentLocation(ctx), typeName, constName);
    }

    @Override
    public void enterWorkerDeclaration(BallerinaParser.WorkerDeclarationContext ctx) {
        if (ctx.exception == null) {
            isWorkerStarted = true;
            modelBuilder.startWorkerUnit();
            modelBuilder.startCallableUnitBody(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitWorkerDeclaration(BallerinaParser.WorkerDeclarationContext ctx) {
        if (ctx.exception == null && ctx.Identifier() != null) {
            modelBuilder.endCallableUnitBody();

            String workerName = ctx.Identifier().get(0).getText();
            String workerParamName = ctx.Identifier().get(1).getText();
            modelBuilder.createWorker(getCurrentLocation(ctx), workerName, workerParamName);
            isWorkerStarted = false;
        }
    }

    @Override
    public void enterTypeName(BallerinaParser.TypeNameContext ctx) {
    }

    @Override
    public void exitTypeName(BallerinaParser.TypeNameContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        if (ctx.typeName() != null) {
            // This is an array type
            SimpleTypeName typeName = typeNameStack.peek();
            typeName.setArrayType((ctx.getChildCount() - 1) / 2);
            return;
        }

        if (ctx.referenceTypeName() != null || ctx.valueTypeName() != null) {
            return;
        }

        // This is 'any' type
        SimpleTypeName typeName = new SimpleTypeName(ctx.getChild(0).getText());
        typeNameStack.push(typeName);
    }

    @Override
    public void enterReferenceTypeName(ReferenceTypeNameContext ctx) {

    }

    @Override
    public void exitReferenceTypeName(ReferenceTypeNameContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        if (ctx.nameReference() != null) {
            BLangModelBuilder.NameReference nameReference = nameReferenceStack.pop();
            SimpleTypeName typeName = new SimpleTypeName(nameReference.getName(),
                    nameReference.getPackageName(), nameReference.getPackagePath());
            typeNameStack.push(typeName);
        }
    }

    @Override
    public void enterValueTypeName(ValueTypeNameContext ctx) {

    }

    @Override
    public void exitValueTypeName(ValueTypeNameContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String valueTypeName = ctx.getChild(0).getText();
        SimpleTypeName simpleTypeName = new SimpleTypeName(valueTypeName);
        typeNameStack.push(simpleTypeName);
    }

    @Override
    public void enterBuiltInReferenceTypeName(BuiltInReferenceTypeNameContext ctx) {

    }

    @Override
    public void exitBuiltInReferenceTypeName(BuiltInReferenceTypeNameContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String builtInRefTypeName = ctx.getChild(0).getText();
        SimpleTypeName simpleTypeName = new SimpleTypeName(builtInRefTypeName);
        typeNameStack.push(simpleTypeName);
    }

    @Override
    public void enterXmlNamespaceName(XmlNamespaceNameContext ctx) {

    }

    @Override
    public void exitXmlNamespaceName(XmlNamespaceNameContext ctx) {

    }

    @Override
    public void enterXmlLocalName(XmlLocalNameContext ctx) {

    }

    @Override
    public void exitXmlLocalName(XmlLocalNameContext ctx) {

    }

    @Override
    public void enterAnnotationAttachment(AnnotationAttachmentContext ctx) {
        if (ctx.exception != null) {
            return;
        }
        modelBuilder.startAnnotationAttachment(getCurrentLocation(ctx));
    }

    @Override
    public void exitAnnotationAttachment(AnnotationAttachmentContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        int attribuesAvailable = ctx.annotationAttributeList() == null ? 0 :
                ctx.annotationAttributeList().annotationAttribute().size();
        modelBuilder.addAnnotationAttachment(getCurrentLocation(ctx), nameReferenceStack.pop(), attribuesAvailable);
    }

    @Override
    public void enterAnnotationAttributeList(AnnotationAttributeListContext ctx) {

    }

    @Override
    public void exitAnnotationAttributeList(AnnotationAttributeListContext ctx) {

    }

    @Override
    public void enterAnnotationAttribute(AnnotationAttributeContext ctx) {

    }

    @Override
    public void exitAnnotationAttribute(AnnotationAttributeContext ctx) {
        if (ctx.exception != null) {
            return;
        }
        
        String key = ctx.Identifier().getText();
        modelBuilder.createAnnotationKeyValue(key);
    }

    @Override
    public void enterAnnotationAttributeValue(AnnotationAttributeValueContext ctx) {
        
    }

    @Override
    public void exitAnnotationAttributeValue(AnnotationAttributeValueContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        ParseTree childContext = ctx.getChild(0);
        if (childContext instanceof SimpleLiteralContext) {
            modelBuilder.createLiteralTypeAttributeValue(getCurrentLocation(ctx));
        } else if (childContext instanceof AnnotationAttachmentContext) {
            modelBuilder.createAnnotationTypeAttributeValue(getCurrentLocation(ctx));
        } else if (childContext instanceof AnnotationAttributeArrayContext) {
            modelBuilder.createArrayTypeAttributeValue(getCurrentLocation(ctx));
        }
    }

    @Override
    public void enterAnnotationAttributeArray(AnnotationAttributeArrayContext ctx) {

    }

    @Override
    public void exitAnnotationAttributeArray(AnnotationAttributeArrayContext ctx) {

    }

    @Override
    public void enterStatement(BallerinaParser.StatementContext ctx) {
    }

    @Override
    public void exitStatement(BallerinaParser.StatementContext ctx) {
    }

    @Override
    public void enterVariableDefinitionStatement(BallerinaParser.VariableDefinitionStatementContext ctx) {

    }

    @Override
    public void exitVariableDefinitionStatement(BallerinaParser.VariableDefinitionStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        SimpleTypeName typeName = typeNameStack.pop();
        String varName = ctx.Identifier().getText();
        boolean exprAvailable = ctx.expression() != null ||
                ctx.connectorInitExpression() != null ||
                ctx.actionInvocation() != null;
        modelBuilder.addVariableDefinitionStmt(getCurrentLocation(ctx), typeName, varName, exprAvailable);
    }

    @Override
    public void enterMapStructLiteral(MapStructLiteralContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startMapStructLiteral();


    }

    @Override
    public void exitMapStructLiteral(MapStructLiteralContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.createMapStructLiteral(getCurrentLocation(ctx));
    }

    @Override
    public void enterMapStructKeyValue(MapStructKeyValueContext ctx) {

    }

    @Override
    public void exitMapStructKeyValue(MapStructKeyValueContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.addMapStructKeyValue(getCurrentLocation(ctx));
    }

    @Override
    public void enterArrayLiteral(ArrayLiteralContext ctx) {

    }

    @Override
    public void exitArrayLiteral(ArrayLiteralContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        boolean argsAvailable = ctx.expressionList() != null;
        modelBuilder.createArrayInitExpr(getCurrentLocation(ctx), argsAvailable);
    }

    @Override
    public void enterConnectorInitExpression(BallerinaParser.ConnectorInitExpressionContext ctx) {

    }

    @Override
    public void exitConnectorInitExpression(BallerinaParser.ConnectorInitExpressionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        boolean argsAvailable = ctx.expressionList() != null;
        BLangModelBuilder.NameReference nameReference = nameReferenceStack.pop();
        SimpleTypeName connectorTypeName = new SimpleTypeName(nameReference.getName(),
                nameReference.getPackageName(), null);
        modelBuilder.createConnectorInitExpr(getCurrentLocation(ctx), connectorTypeName, argsAvailable);
    }

    @Override
    public void enterAssignmentStatement(BallerinaParser.AssignmentStatementContext ctx) {
    }

    @Override
    public void exitAssignmentStatement(BallerinaParser.AssignmentStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.createAssignmentStmt(getCurrentLocation(ctx));
    }

    @Override
    public void enterVariableReferenceList(BallerinaParser.VariableReferenceListContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startVarRefList();
    }

    @Override
    public void exitVariableReferenceList(BallerinaParser.VariableReferenceListContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        int noOfArguments = getNoOfArgumentsInList(ctx);
        modelBuilder.endVarRefList(noOfArguments);
    }

    @Override
    public void enterIfElseStatement(BallerinaParser.IfElseStatementContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startIfElseStmt(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitIfElseStatement(BallerinaParser.IfElseStatementContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.addIfElseStmt();
        }
    }

    @Override
    public void enterIfClause(BallerinaParser.IfClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startIfClause(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitIfClause(BallerinaParser.IfClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.addIfClause();
        }
    }

    @Override
    public void enterElseIfClause(BallerinaParser.ElseIfClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startElseIfClause(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitElseIfClause(BallerinaParser.ElseIfClauseContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.addElseIfClause();
    }

    @Override
    public void enterElseClause(BallerinaParser.ElseClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startElseClause(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitElseClause(BallerinaParser.ElseClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.addElseClause();
        }
    }

    @Override
    public void enterIterateStatement(BallerinaParser.IterateStatementContext ctx) {
    }

    @Override
    public void exitIterateStatement(BallerinaParser.IterateStatementContext ctx) {
    }

    @Override
    public void enterWhileStatement(BallerinaParser.WhileStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.startWhileStmt(getCurrentLocation(ctx));
    }

    @Override
    public void exitWhileStatement(BallerinaParser.WhileStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.createWhileStmt(getCurrentLocation(ctx));
    }

    @Override
    public void enterContinueStatement(ContinueStatementContext ctx) {

    }

    @Override
    public void exitContinueStatement(ContinueStatementContext ctx) {

    }

    @Override
    public void enterBreakStatement(BallerinaParser.BreakStatementContext ctx) {
    }

    @Override
    public void exitBreakStatement(BallerinaParser.BreakStatementContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.createBreakStmt(getCurrentLocation(ctx));
        }
    }

    @Override
    public void enterForkJoinStatement(BallerinaParser.ForkJoinStatementContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startForkJoinStmt(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitForkJoinStatement(BallerinaParser.ForkJoinStatementContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.endForkJoinStmt();
        }
    }

    @Override
    public void enterJoinClause(BallerinaParser.JoinClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startJoinClause(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitJoinClause(BallerinaParser.JoinClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.endJoinClause(getCurrentLocation(ctx), typeNameStack.pop(), ctx.Identifier().getText());
        }
    }

    public void enterAnyJoinCondition(BallerinaParser.AnyJoinConditionContext ctx) {

    }

    @Override
    public void exitAnyJoinCondition(BallerinaParser.AnyJoinConditionContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.createAnyJoinCondition("any", ctx.IntegerLiteral().getText(), getCurrentLocation(ctx));
            for (TerminalNode t : ctx.Identifier()) {
                modelBuilder.createJoinWorkers(t.getText());
            }
        }
    }

    @Override
    public void enterAllJoinCondition(BallerinaParser.AllJoinConditionContext ctx) {

    }

    @Override
    public void exitAllJoinCondition(BallerinaParser.AllJoinConditionContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.createAllJoinCondition("all");
            for (TerminalNode t : ctx.Identifier()) {
                modelBuilder.createJoinWorkers(t.getText());
            }
        }

    }

    @Override
    public void enterTimeoutClause(BallerinaParser.TimeoutClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startTimeoutClause(getCurrentLocation(ctx));
        }
    }

    @Override
    public void exitTimeoutClause(BallerinaParser.TimeoutClauseContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.endTimeoutClause(getCurrentLocation(ctx), typeNameStack.pop(), ctx.Identifier().getText());
        }
    }

    @Override
    public void enterTryCatchStatement(BallerinaParser.TryCatchStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }
        modelBuilder.startTryCatchStmt(getCurrentLocation(ctx));
    }

    @Override
    public void exitTryCatchStatement(BallerinaParser.TryCatchStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }
        modelBuilder.addTryCatchStmt();
    }

    @Override
    public void enterCatchClause(BallerinaParser.CatchClauseContext ctx) {
        if (ctx.exception != null) {
            return;
        }
        modelBuilder.startCatchClause(getCurrentLocation(ctx));
    }

    @Override
    public void exitCatchClause(BallerinaParser.CatchClauseContext ctx) {
        if (ctx.exception != null) {
            return;
        }
        String key = ctx.Identifier().getText();
        // FIX ME
        SimpleTypeName simpleTypeName = new SimpleTypeName("exception");
        modelBuilder.addCatchClause(getCurrentLocation(ctx), simpleTypeName, key);
    }

    @Override
    public void enterThrowStatement(BallerinaParser.ThrowStatementContext ctx) {
    }

    @Override
    public void exitThrowStatement(BallerinaParser.ThrowStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }
        modelBuilder.createThrowStmt(getCurrentLocation(ctx));
    }

    @Override
    public void enterReturnStatement(BallerinaParser.ReturnStatementContext ctx) {
    }

    @Override
    public void exitReturnStatement(BallerinaParser.ReturnStatementContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.createReturnStmt(getCurrentLocation(ctx));
        }
    }

    @Override
    public void enterReplyStatement(BallerinaParser.ReplyStatementContext ctx) {
    }

    @Override
    public void exitReplyStatement(BallerinaParser.ReplyStatementContext ctx) {
        // Here the expression is only a message reference
        //modelBuilder.createVarRefExpr();
        if (ctx.exception == null) {
            modelBuilder.createReplyStmt(getCurrentLocation(ctx));
        }
    }

    @Override
    public void enterWorkerInteractionStatement(BallerinaParser.WorkerInteractionStatementContext ctx) {
    }

    @Override
    public void exitWorkerInteractionStatement(BallerinaParser.WorkerInteractionStatementContext ctx) {
    }

    @Override
    public void enterTriggerWorker(BallerinaParser.TriggerWorkerContext ctx) {
    }

    @Override
    public void exitTriggerWorker(BallerinaParser.TriggerWorkerContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.createWorkerInvocationStmt(ctx.Identifier(0).getText(), ctx.Identifier(1).getText(),
                    getCurrentLocation(ctx));
        }
    }

    @Override
    public void enterWorkerReply(BallerinaParser.WorkerReplyContext ctx) {
    }

    @Override
    public void exitWorkerReply(BallerinaParser.WorkerReplyContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.createWorkerReplyStmt(ctx.Identifier(0).getText(), ctx.Identifier(1).getText(),
                    getCurrentLocation(ctx));
        }
    }

    @Override
    public void enterCommentStatement(BallerinaParser.CommentStatementContext ctx) {
    }

    @Override
    public void exitCommentStatement(BallerinaParser.CommentStatementContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.addCommentStmt(getCurrentLocation(ctx), ctx.getText());
        }
    }

    @Override
    public void enterStructFieldIdentifier(BallerinaParser.StructFieldIdentifierContext ctx) {
    }

    @Override
    public void exitStructFieldIdentifier(BallerinaParser.StructFieldIdentifierContext ctx) {
        if (ctx.exception != null || ctx.getChild(0) == null) {
            return;
        }
        modelBuilder.createStructFieldRefExpr(getCurrentLocation(ctx));
    }

    @Override
    public void enterSimpleVariableIdentifier(BallerinaParser.SimpleVariableIdentifierContext ctx) {
    }

    @Override
    public void exitSimpleVariableIdentifier(BallerinaParser.SimpleVariableIdentifierContext ctx) {
        if (ctx.exception == null) {
            String varName = ctx.getText();
            modelBuilder.createVarRefExpr(getCurrentLocation(ctx), varName);
        }
    }

    @Override
    public void enterMapArrayVariableIdentifier(BallerinaParser.MapArrayVariableIdentifierContext ctx) {
    }

    @Override
    public void exitMapArrayVariableIdentifier(BallerinaParser.MapArrayVariableIdentifierContext ctx) {
        if (ctx.exception == null && ctx.Identifier() != null) {
            String mapArrayVarName = ctx.Identifier().getText();
            int dimensions = (ctx.getChildCount() - 1) / 3;
            modelBuilder.createMapArrayVarRefExpr(getCurrentLocation(ctx), mapArrayVarName, dimensions);
        }
    }

    @Override
    public void enterExpressionList(BallerinaParser.ExpressionListContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.startExprList();
        }
    }

    @Override
    public void exitExpressionList(BallerinaParser.ExpressionListContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        int noOfArguments = getNoOfArgumentsInList(ctx);
        modelBuilder.endExprList(noOfArguments);
    }

    @Override
    public void enterFunctionInvocationStatement(BallerinaParser.FunctionInvocationStatementContext ctx) {
    }

    @Override
    public void exitFunctionInvocationStatement(BallerinaParser.FunctionInvocationStatementContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        boolean argsAvailable = ctx.expressionList() != null;
        modelBuilder.createFunctionInvocationStmt(getCurrentLocation(ctx), nameReferenceStack.pop(), argsAvailable);
    }

    @Override
    public void enterActionInvocationStatement(BallerinaParser.ActionInvocationStatementContext ctx) {
        processingActionInvocationStmt = true;
    }

    @Override
    public void exitActionInvocationStatement(BallerinaParser.ActionInvocationStatementContext ctx) {
        processingActionInvocationStmt = false;
    }

    @Override
    public void enterActionInvocation(BallerinaParser.ActionInvocationContext ctx) {
    }

    @Override
    public void exitActionInvocation(BallerinaParser.ActionInvocationContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        String actionName = ctx.Identifier().getText();
        BLangModelBuilder.NameReference nameReference = nameReferenceStack.pop();
        NodeLocation nodeLocation = getCurrentLocation(ctx);
        boolean argsAvailable = ctx.expressionList() != null;

        if (processingActionInvocationStmt) {
            modelBuilder.createActionInvocationStmt(nodeLocation, nameReference, actionName, argsAvailable);
        } else {
            modelBuilder.addActionInvocationExpr(nodeLocation, nameReference, actionName, argsAvailable);
        }
    }

    @Override
    public void enterBacktickString(BallerinaParser.BacktickStringContext ctx) {
    }

    @Override
    public void exitBacktickString(BallerinaParser.BacktickStringContext ctx) {
        if (ctx.exception == null) {
            modelBuilder.createBacktickExpr(getCurrentLocation(ctx), ctx.BacktickStringLiteral().getText());
        }
    }

    @Override
    public void enterBinaryDivMulModExpression(BallerinaParser.BinaryDivMulModExpressionContext ctx) {

    }

    @Override
    public void exitBinaryDivMulModExpression(BallerinaParser.BinaryDivMulModExpressionContext ctx) {
        if (ctx.exception == null) {
            createBinaryExpr(ctx);
        }
    }

    @Override
    public void enterBinaryOrExpression(BallerinaParser.BinaryOrExpressionContext ctx) {
    }

    @Override
    public void exitBinaryOrExpression(BallerinaParser.BinaryOrExpressionContext ctx) {
        if (ctx.exception == null) {
            createBinaryExpr(ctx);
        }

    }

    @Override
    public void enterValueTypeTypeExpression(ValueTypeTypeExpressionContext ctx) {

    }

    @Override
    public void exitValueTypeTypeExpression(ValueTypeTypeExpressionContext ctx) {

    }

    @Override
    public void enterTemplateExpression(BallerinaParser.TemplateExpressionContext ctx) {
    }

    @Override
    public void exitTemplateExpression(BallerinaParser.TemplateExpressionContext ctx) {
    }

    @Override
    public void enterSimpleLiteralExpression(SimpleLiteralExpressionContext ctx) {

    }

    @Override
    public void exitSimpleLiteralExpression(SimpleLiteralExpressionContext ctx) {

    }

    @Override
    public void enterFunctionInvocationExpression(BallerinaParser.FunctionInvocationExpressionContext ctx) {
    }

    @Override
    public void exitFunctionInvocationExpression(BallerinaParser.FunctionInvocationExpressionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        boolean argsAvailable = ctx.expressionList() != null;
        modelBuilder.addFunctionInvocationExpr(getCurrentLocation(ctx), nameReferenceStack.pop(), argsAvailable);
    }

    @Override
    public void enterBinaryEqualExpression(BallerinaParser.BinaryEqualExpressionContext ctx) {
    }

    @Override
    public void exitBinaryEqualExpression(BallerinaParser.BinaryEqualExpressionContext ctx) {
        if (ctx.exception == null) {
            createBinaryExpr(ctx);
        }
    }

    @Override
    public void enterArrayLiteralExpression(ArrayLiteralExpressionContext ctx) {

    }

    @Override
    public void exitArrayLiteralExpression(ArrayLiteralExpressionContext ctx) {

    }

    @Override
    public void enterBracedExpression(BallerinaParser.BracedExpressionContext ctx) {
    }

    @Override
    public void exitBracedExpression(BallerinaParser.BracedExpressionContext ctx) {
    }

    @Override
    public void enterVariableReferenceExpression(BallerinaParser.VariableReferenceExpressionContext ctx) {
    }

    @Override
    public void exitVariableReferenceExpression(BallerinaParser.VariableReferenceExpressionContext ctx) {
//        modelBuilder.createVarRefExpr();
    }

    @Override
    public void enterMapStructLiteralExpression(MapStructLiteralExpressionContext ctx) {

    }

    @Override
    public void exitMapStructLiteralExpression(MapStructLiteralExpressionContext ctx) {

    }

    @Override
    public void enterTypeCastingExpression(BallerinaParser.TypeCastingExpressionContext ctx) {
    }

    @Override
    public void exitTypeCastingExpression(BallerinaParser.TypeCastingExpressionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        modelBuilder.createTypeCastExpr(getCurrentLocation(ctx), typeNameStack.pop());
    }

    @Override
    public void enterBinaryAndExpression(BallerinaParser.BinaryAndExpressionContext ctx) {
    }

    @Override
    public void exitBinaryAndExpression(BallerinaParser.BinaryAndExpressionContext ctx) {
        if (ctx.exception == null) {
            createBinaryExpr(ctx);
        }
    }

    @Override
    public void enterBinaryAddSubExpression(BallerinaParser.BinaryAddSubExpressionContext ctx) {

    }

    @Override
    public void exitBinaryAddSubExpression(BallerinaParser.BinaryAddSubExpressionContext ctx) {
        if (ctx.exception == null) {
            createBinaryExpr(ctx);
        }
    }

    @Override
    public void enterBinaryCompareExpression(BallerinaParser.BinaryCompareExpressionContext ctx) {

    }

    @Override
    public void exitBinaryCompareExpression(BallerinaParser.BinaryCompareExpressionContext ctx) {
        if (ctx.exception == null) {
            createBinaryExpr(ctx);
        }
    }

    @Override
    public void enterBuiltInReferenceTypeTypeExpression(BuiltInReferenceTypeTypeExpressionContext ctx) {

    }

    @Override
    public void exitBuiltInReferenceTypeTypeExpression(BuiltInReferenceTypeTypeExpressionContext ctx) {

    }

    @Override
    public void enterUnaryExpression(BallerinaParser.UnaryExpressionContext ctx) {
    }

    @Override
    public void exitUnaryExpression(BallerinaParser.UnaryExpressionContext ctx) {
        if (ctx.exception == null) {
            String op = ctx.getChild(0).getText();
            modelBuilder.createUnaryExpr(getCurrentLocation(ctx), op);
        }
    }

    @Override
    public void enterBinaryPowExpression(BallerinaParser.BinaryPowExpressionContext ctx) {
    }

    @Override
    public void exitBinaryPowExpression(BallerinaParser.BinaryPowExpressionContext ctx) {
        if (ctx.exception == null) {
            createBinaryExpr(ctx);
        }
    }

    @Override
    public void enterNameReference(NameReferenceContext ctx) {

    }

    @Override
    public void exitNameReference(NameReferenceContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        BLangModelBuilder.NameReference nameReference;
        if (ctx.Identifier().size() == 2) {
            String pkgName = ctx.Identifier(0).getText();
            String name = ctx.Identifier(1).getText();
            nameReference = new BLangModelBuilder.NameReference(pkgName, name);

        } else {
            String name = ctx.Identifier(0).getText();
            nameReference = new BLangModelBuilder.NameReference(null, name);
        }
        modelBuilder.validateAndSetPackagePath(getCurrentLocation(ctx), nameReference);
        nameReferenceStack.push(nameReference);
    }

    @Override
    public void enterReturnParameters(BallerinaParser.ReturnParametersContext ctx) {
        processingReturnParams = true;
    }

    @Override
    public void exitReturnParameters(BallerinaParser.ReturnParametersContext ctx) {
        processingReturnParams = false;
    }

    @Override
    public void enterReturnTypeList(BallerinaParser.ReturnTypeListContext ctx) {

    }

    @Override
    public void exitReturnTypeList(BallerinaParser.ReturnTypeListContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        List<SimpleTypeName> list = new ArrayList<>(typeNameStack);
        modelBuilder.addReturnTypes(getCurrentLocation(ctx), list.toArray(new SimpleTypeName[0]));
        typeNameStack.removeAllElements();
    }

    @Override
    public void enterParameterList(BallerinaParser.ParameterListContext ctx) {
    }

    @Override
    public void exitParameterList(BallerinaParser.ParameterListContext ctx) {
    }

    @Override
    public void enterParameter(BallerinaParser.ParameterContext ctx) {
    }

    @Override
    public void exitParameter(BallerinaParser.ParameterContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        int annotationCount = ctx.annotationAttachment() != null ? ctx.annotationAttachment().size() : 0;
        modelBuilder.addParam(getCurrentLocation(ctx), typeNameStack.pop(),
                ctx.Identifier().getText(), annotationCount, processingReturnParams);
    }

    @Override
    public void enterFieldDefinition(FieldDefinitionContext ctx) {

    }

    @Override
    public void exitFieldDefinition(FieldDefinitionContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        SimpleTypeName typeName = typeNameStack.pop();
        String fieldName = ctx.Identifier().getText();
        boolean isDefaultValueAvalibale = ctx.simpleLiteral() != null;
        modelBuilder.addFieldDefinition(getCurrentLocation(ctx), typeName, fieldName, isDefaultValueAvalibale);
    }

    @Override
    public void enterSimpleLiteral(SimpleLiteralContext ctx) {

    }

    @Override
    public void exitSimpleLiteral(SimpleLiteralContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        createBasicLiteral(ctx);
    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {
    }

    @Override
    public void visitErrorNode(ErrorNode errorNode) {
    }

    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {
    }

    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {
    }

    protected void createBinaryExpr(ParserRuleContext ctx) {
        if (ctx.exception == null && ctx.getChild(1) != null) {
            String opStr = ctx.getChild(1).getText();
            modelBuilder.createBinaryExpr(getCurrentLocation(ctx), opStr);
        }
    }

    protected void createBasicLiteral(BallerinaParser.SimpleLiteralContext ctx) {
        if (ctx.exception != null) {
            return;
        }

        TerminalNode terminalNode = ctx.IntegerLiteral();
        if (terminalNode != null) {
            modelBuilder.createIntegerLiteral(getCurrentLocation(ctx), terminalNode.getText());
            return;
        }

        terminalNode = ctx.FloatingPointLiteral();
        if (terminalNode != null) {
            modelBuilder.createFloatLiteral(getCurrentLocation(ctx), terminalNode.getText());
            return;
        }

        terminalNode = ctx.QuotedStringLiteral();
        if (terminalNode != null) {
            String stringLiteral = terminalNode.getText();
            stringLiteral = stringLiteral.substring(1, stringLiteral.length() - 1);
            stringLiteral = StringEscapeUtils.unescapeJava(stringLiteral);
            modelBuilder.createStringLiteral(getCurrentLocation(ctx), stringLiteral);
            return;
        }

        terminalNode = ctx.BooleanLiteral();
        if (terminalNode != null) {
            modelBuilder.createBooleanLiteral(getCurrentLocation(ctx), terminalNode.getText());
            return;
        }

        terminalNode = ctx.NullLiteral();
        if (terminalNode != null) {
            modelBuilder.createNullLiteral(getCurrentLocation(ctx), terminalNode.getText());
        }
    }

    protected NodeLocation getCurrentLocation(ParserRuleContext ctx) {
        String fileName = ctx.getStart().getInputStream().getSourceName();
        int lineNo = ctx.getStart().getLine();
        return new NodeLocation(packageDirPath, fileName, lineNo);
    }

    protected NodeLocation getCurrentLocation(TerminalNode node) {
        String fileName = node.getSymbol().getInputStream().getSourceName();
        int lineNo = node.getSymbol().getLine();
        return new NodeLocation(packageDirPath, fileName, lineNo);
    }

    protected int getNoOfArgumentsInList(ParserRuleContext ctx) {
        // Here is the production for the argument list
        // argumentList
        //    :   '(' expressionList ')'
        //    ;
        //
        // expressionList
        //    :   expression (',' expression)*
        //    ;

        // Now we need to calculate the number of arguments in a function or an action.
        // We can do the by getting the children of expressionList from the ctx
        // The following count includes the token for the ","  as well.
        int childCountExprList = ctx.getChildCount();

        // Therefore we need to subtract the number of ","
        // e.g. (a, b)          => childCount = 3, noOfArguments = 2;
        //      (a, b, c)       => childCount = 5, noOfArguments = 3;
        //      (a, b, c, d)    => childCount = 7, noOfArguments = 4;
        // Here childCount is always an odd number.
        // noOfArguments = childCount mod 2 + 1
        return childCountExprList / 2 + 1;
    }
}
