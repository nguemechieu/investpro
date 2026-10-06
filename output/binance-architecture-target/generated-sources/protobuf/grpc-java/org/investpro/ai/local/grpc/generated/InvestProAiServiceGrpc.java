package org.investpro.ai.local.grpc.generated;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class InvestProAiServiceGrpc {

  private InvestProAiServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "investpro.ai.InvestProAiService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.HealthRequest,
      org.investpro.ai.local.grpc.generated.HealthResponse> getHealthMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Health",
      requestType = org.investpro.ai.local.grpc.generated.HealthRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.HealthResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.HealthRequest,
      org.investpro.ai.local.grpc.generated.HealthResponse> getHealthMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.HealthRequest, org.investpro.ai.local.grpc.generated.HealthResponse> getHealthMethod;
    if ((getHealthMethod = InvestProAiServiceGrpc.getHealthMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getHealthMethod = InvestProAiServiceGrpc.getHealthMethod) == null) {
          InvestProAiServiceGrpc.getHealthMethod = getHealthMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.HealthRequest, org.investpro.ai.local.grpc.generated.HealthResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Health"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.HealthRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.HealthResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("Health"))
              .build();
        }
      }
    }
    return getHealthMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.SignalReviewRequest,
      org.investpro.ai.local.grpc.generated.SignalReviewResponse> getAnalyzeSignalMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AnalyzeSignal",
      requestType = org.investpro.ai.local.grpc.generated.SignalReviewRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.SignalReviewResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.SignalReviewRequest,
      org.investpro.ai.local.grpc.generated.SignalReviewResponse> getAnalyzeSignalMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.SignalReviewRequest, org.investpro.ai.local.grpc.generated.SignalReviewResponse> getAnalyzeSignalMethod;
    if ((getAnalyzeSignalMethod = InvestProAiServiceGrpc.getAnalyzeSignalMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getAnalyzeSignalMethod = InvestProAiServiceGrpc.getAnalyzeSignalMethod) == null) {
          InvestProAiServiceGrpc.getAnalyzeSignalMethod = getAnalyzeSignalMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.SignalReviewRequest, org.investpro.ai.local.grpc.generated.SignalReviewResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AnalyzeSignal"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.SignalReviewRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.SignalReviewResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("AnalyzeSignal"))
              .build();
        }
      }
    }
    return getAnalyzeSignalMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest,
      org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> getDetectRegimeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DetectRegime",
      requestType = org.investpro.ai.local.grpc.generated.RegimeDetectionRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.RegimeDetectionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest,
      org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> getDetectRegimeMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest, org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> getDetectRegimeMethod;
    if ((getDetectRegimeMethod = InvestProAiServiceGrpc.getDetectRegimeMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getDetectRegimeMethod = InvestProAiServiceGrpc.getDetectRegimeMethod) == null) {
          InvestProAiServiceGrpc.getDetectRegimeMethod = getDetectRegimeMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest, org.investpro.ai.local.grpc.generated.RegimeDetectionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DetectRegime"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.RegimeDetectionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.RegimeDetectionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("DetectRegime"))
              .build();
        }
      }
    }
    return getDetectRegimeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.StrategyReviewRequest,
      org.investpro.ai.local.grpc.generated.StrategyReviewResponse> getReviewStrategyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReviewStrategy",
      requestType = org.investpro.ai.local.grpc.generated.StrategyReviewRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.StrategyReviewResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.StrategyReviewRequest,
      org.investpro.ai.local.grpc.generated.StrategyReviewResponse> getReviewStrategyMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.StrategyReviewRequest, org.investpro.ai.local.grpc.generated.StrategyReviewResponse> getReviewStrategyMethod;
    if ((getReviewStrategyMethod = InvestProAiServiceGrpc.getReviewStrategyMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getReviewStrategyMethod = InvestProAiServiceGrpc.getReviewStrategyMethod) == null) {
          InvestProAiServiceGrpc.getReviewStrategyMethod = getReviewStrategyMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.StrategyReviewRequest, org.investpro.ai.local.grpc.generated.StrategyReviewResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReviewStrategy"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.StrategyReviewRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.StrategyReviewResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("ReviewStrategy"))
              .build();
        }
      }
    }
    return getReviewStrategyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.StrategyRankingRequest,
      org.investpro.ai.local.grpc.generated.StrategyRankingResponse> getRankStrategiesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RankStrategies",
      requestType = org.investpro.ai.local.grpc.generated.StrategyRankingRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.StrategyRankingResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.StrategyRankingRequest,
      org.investpro.ai.local.grpc.generated.StrategyRankingResponse> getRankStrategiesMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.StrategyRankingRequest, org.investpro.ai.local.grpc.generated.StrategyRankingResponse> getRankStrategiesMethod;
    if ((getRankStrategiesMethod = InvestProAiServiceGrpc.getRankStrategiesMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getRankStrategiesMethod = InvestProAiServiceGrpc.getRankStrategiesMethod) == null) {
          InvestProAiServiceGrpc.getRankStrategiesMethod = getRankStrategiesMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.StrategyRankingRequest, org.investpro.ai.local.grpc.generated.StrategyRankingResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RankStrategies"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.StrategyRankingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.StrategyRankingResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("RankStrategies"))
              .build();
        }
      }
    }
    return getRankStrategiesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.BacktestReviewRequest,
      org.investpro.ai.local.grpc.generated.BacktestReviewResponse> getReviewBacktestMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReviewBacktest",
      requestType = org.investpro.ai.local.grpc.generated.BacktestReviewRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.BacktestReviewResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.BacktestReviewRequest,
      org.investpro.ai.local.grpc.generated.BacktestReviewResponse> getReviewBacktestMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.BacktestReviewRequest, org.investpro.ai.local.grpc.generated.BacktestReviewResponse> getReviewBacktestMethod;
    if ((getReviewBacktestMethod = InvestProAiServiceGrpc.getReviewBacktestMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getReviewBacktestMethod = InvestProAiServiceGrpc.getReviewBacktestMethod) == null) {
          InvestProAiServiceGrpc.getReviewBacktestMethod = getReviewBacktestMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.BacktestReviewRequest, org.investpro.ai.local.grpc.generated.BacktestReviewResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReviewBacktest"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.BacktestReviewRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.BacktestReviewResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("ReviewBacktest"))
              .build();
        }
      }
    }
    return getReviewBacktestMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RiskScoreRequest,
      org.investpro.ai.local.grpc.generated.RiskScoreResponse> getScoreRiskMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ScoreRisk",
      requestType = org.investpro.ai.local.grpc.generated.RiskScoreRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.RiskScoreResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RiskScoreRequest,
      org.investpro.ai.local.grpc.generated.RiskScoreResponse> getScoreRiskMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RiskScoreRequest, org.investpro.ai.local.grpc.generated.RiskScoreResponse> getScoreRiskMethod;
    if ((getScoreRiskMethod = InvestProAiServiceGrpc.getScoreRiskMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getScoreRiskMethod = InvestProAiServiceGrpc.getScoreRiskMethod) == null) {
          InvestProAiServiceGrpc.getScoreRiskMethod = getScoreRiskMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.RiskScoreRequest, org.investpro.ai.local.grpc.generated.RiskScoreResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ScoreRisk"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.RiskScoreRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.RiskScoreResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("ScoreRisk"))
              .build();
        }
      }
    }
    return getScoreRiskMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest,
      org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse> getDetectAnomalyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DetectAnomaly",
      requestType = org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest,
      org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse> getDetectAnomalyMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest, org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse> getDetectAnomalyMethod;
    if ((getDetectAnomalyMethod = InvestProAiServiceGrpc.getDetectAnomalyMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getDetectAnomalyMethod = InvestProAiServiceGrpc.getDetectAnomalyMethod) == null) {
          InvestProAiServiceGrpc.getDetectAnomalyMethod = getDetectAnomalyMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest, org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DetectAnomaly"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("DetectAnomaly"))
              .build();
        }
      }
    }
    return getDetectAnomalyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.SignalReviewRequest,
      org.investpro.ai.local.grpc.generated.SignalReviewResponse> getStreamSignalsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamSignals",
      requestType = org.investpro.ai.local.grpc.generated.SignalReviewRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.SignalReviewResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.SignalReviewRequest,
      org.investpro.ai.local.grpc.generated.SignalReviewResponse> getStreamSignalsMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.SignalReviewRequest, org.investpro.ai.local.grpc.generated.SignalReviewResponse> getStreamSignalsMethod;
    if ((getStreamSignalsMethod = InvestProAiServiceGrpc.getStreamSignalsMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getStreamSignalsMethod = InvestProAiServiceGrpc.getStreamSignalsMethod) == null) {
          InvestProAiServiceGrpc.getStreamSignalsMethod = getStreamSignalsMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.SignalReviewRequest, org.investpro.ai.local.grpc.generated.SignalReviewResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamSignals"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.SignalReviewRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.SignalReviewResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("StreamSignals"))
              .build();
        }
      }
    }
    return getStreamSignalsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest,
      org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> getStreamMarketStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamMarketState",
      requestType = org.investpro.ai.local.grpc.generated.RegimeDetectionRequest.class,
      responseType = org.investpro.ai.local.grpc.generated.RegimeDetectionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest,
      org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> getStreamMarketStateMethod() {
    io.grpc.MethodDescriptor<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest, org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> getStreamMarketStateMethod;
    if ((getStreamMarketStateMethod = InvestProAiServiceGrpc.getStreamMarketStateMethod) == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        if ((getStreamMarketStateMethod = InvestProAiServiceGrpc.getStreamMarketStateMethod) == null) {
          InvestProAiServiceGrpc.getStreamMarketStateMethod = getStreamMarketStateMethod =
              io.grpc.MethodDescriptor.<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest, org.investpro.ai.local.grpc.generated.RegimeDetectionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamMarketState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.RegimeDetectionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.investpro.ai.local.grpc.generated.RegimeDetectionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InvestProAiServiceMethodDescriptorSupplier("StreamMarketState"))
              .build();
        }
      }
    }
    return getStreamMarketStateMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static InvestProAiServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceStub>() {
        @java.lang.Override
        public InvestProAiServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new InvestProAiServiceStub(channel, callOptions);
        }
      };
    return InvestProAiServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static InvestProAiServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceBlockingV2Stub>() {
        @java.lang.Override
        public InvestProAiServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new InvestProAiServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return InvestProAiServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static InvestProAiServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceBlockingStub>() {
        @java.lang.Override
        public InvestProAiServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new InvestProAiServiceBlockingStub(channel, callOptions);
        }
      };
    return InvestProAiServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static InvestProAiServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<InvestProAiServiceFutureStub>() {
        @java.lang.Override
        public InvestProAiServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new InvestProAiServiceFutureStub(channel, callOptions);
        }
      };
    return InvestProAiServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void health(org.investpro.ai.local.grpc.generated.HealthRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.HealthResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHealthMethod(), responseObserver);
    }

    /**
     */
    default void analyzeSignal(org.investpro.ai.local.grpc.generated.SignalReviewRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAnalyzeSignalMethod(), responseObserver);
    }

    /**
     */
    default void detectRegime(org.investpro.ai.local.grpc.generated.RegimeDetectionRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDetectRegimeMethod(), responseObserver);
    }

    /**
     */
    default void reviewStrategy(org.investpro.ai.local.grpc.generated.StrategyReviewRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.StrategyReviewResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReviewStrategyMethod(), responseObserver);
    }

    /**
     */
    default void rankStrategies(org.investpro.ai.local.grpc.generated.StrategyRankingRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.StrategyRankingResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRankStrategiesMethod(), responseObserver);
    }

    /**
     */
    default void reviewBacktest(org.investpro.ai.local.grpc.generated.BacktestReviewRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.BacktestReviewResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReviewBacktestMethod(), responseObserver);
    }

    /**
     */
    default void scoreRisk(org.investpro.ai.local.grpc.generated.RiskScoreRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RiskScoreResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getScoreRiskMethod(), responseObserver);
    }

    /**
     */
    default void detectAnomaly(org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDetectAnomalyMethod(), responseObserver);
    }

    /**
     * <pre>
     * Extension points for future low-latency streaming workflows.
     * </pre>
     */
    default io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewRequest> streamSignals(
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewResponse> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getStreamSignalsMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest> streamMarketState(
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getStreamMarketStateMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service InvestProAiService.
   */
  public static abstract class InvestProAiServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return InvestProAiServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service InvestProAiService.
   */
  public static final class InvestProAiServiceStub
      extends io.grpc.stub.AbstractAsyncStub<InvestProAiServiceStub> {
    private InvestProAiServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected InvestProAiServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new InvestProAiServiceStub(channel, callOptions);
    }

    /**
     */
    public void health(org.investpro.ai.local.grpc.generated.HealthRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.HealthResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHealthMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void analyzeSignal(org.investpro.ai.local.grpc.generated.SignalReviewRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAnalyzeSignalMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void detectRegime(org.investpro.ai.local.grpc.generated.RegimeDetectionRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDetectRegimeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void reviewStrategy(org.investpro.ai.local.grpc.generated.StrategyReviewRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.StrategyReviewResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReviewStrategyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void rankStrategies(org.investpro.ai.local.grpc.generated.StrategyRankingRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.StrategyRankingResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRankStrategiesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void reviewBacktest(org.investpro.ai.local.grpc.generated.BacktestReviewRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.BacktestReviewResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReviewBacktestMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void scoreRisk(org.investpro.ai.local.grpc.generated.RiskScoreRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RiskScoreResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getScoreRiskMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void detectAnomaly(org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest request,
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDetectAnomalyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Extension points for future low-latency streaming workflows.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewRequest> streamSignals(
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewResponse> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getStreamSignalsMethod(), getCallOptions()), responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest> streamMarketState(
        io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getStreamMarketStateMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service InvestProAiService.
   */
  public static final class InvestProAiServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<InvestProAiServiceBlockingV2Stub> {
    private InvestProAiServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected InvestProAiServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new InvestProAiServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.HealthResponse health(org.investpro.ai.local.grpc.generated.HealthRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getHealthMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.SignalReviewResponse analyzeSignal(org.investpro.ai.local.grpc.generated.SignalReviewRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getAnalyzeSignalMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.RegimeDetectionResponse detectRegime(org.investpro.ai.local.grpc.generated.RegimeDetectionRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getDetectRegimeMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.StrategyReviewResponse reviewStrategy(org.investpro.ai.local.grpc.generated.StrategyReviewRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getReviewStrategyMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.StrategyRankingResponse rankStrategies(org.investpro.ai.local.grpc.generated.StrategyRankingRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getRankStrategiesMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.BacktestReviewResponse reviewBacktest(org.investpro.ai.local.grpc.generated.BacktestReviewRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getReviewBacktestMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.RiskScoreResponse scoreRisk(org.investpro.ai.local.grpc.generated.RiskScoreRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getScoreRiskMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse detectAnomaly(org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getDetectAnomalyMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Extension points for future low-latency streaming workflows.
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<org.investpro.ai.local.grpc.generated.SignalReviewRequest, org.investpro.ai.local.grpc.generated.SignalReviewResponse>
        streamSignals() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getStreamSignalsMethod(), getCallOptions());
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<org.investpro.ai.local.grpc.generated.RegimeDetectionRequest, org.investpro.ai.local.grpc.generated.RegimeDetectionResponse>
        streamMarketState() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getStreamMarketStateMethod(), getCallOptions());
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service InvestProAiService.
   */
  public static final class InvestProAiServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<InvestProAiServiceBlockingStub> {
    private InvestProAiServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected InvestProAiServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new InvestProAiServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.HealthResponse health(org.investpro.ai.local.grpc.generated.HealthRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHealthMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.SignalReviewResponse analyzeSignal(org.investpro.ai.local.grpc.generated.SignalReviewRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAnalyzeSignalMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.RegimeDetectionResponse detectRegime(org.investpro.ai.local.grpc.generated.RegimeDetectionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDetectRegimeMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.StrategyReviewResponse reviewStrategy(org.investpro.ai.local.grpc.generated.StrategyReviewRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReviewStrategyMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.StrategyRankingResponse rankStrategies(org.investpro.ai.local.grpc.generated.StrategyRankingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRankStrategiesMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.BacktestReviewResponse reviewBacktest(org.investpro.ai.local.grpc.generated.BacktestReviewRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReviewBacktestMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.RiskScoreResponse scoreRisk(org.investpro.ai.local.grpc.generated.RiskScoreRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getScoreRiskMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse detectAnomaly(org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDetectAnomalyMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service InvestProAiService.
   */
  public static final class InvestProAiServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<InvestProAiServiceFutureStub> {
    private InvestProAiServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected InvestProAiServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new InvestProAiServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.HealthResponse> health(
        org.investpro.ai.local.grpc.generated.HealthRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHealthMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.SignalReviewResponse> analyzeSignal(
        org.investpro.ai.local.grpc.generated.SignalReviewRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAnalyzeSignalMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.RegimeDetectionResponse> detectRegime(
        org.investpro.ai.local.grpc.generated.RegimeDetectionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDetectRegimeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.StrategyReviewResponse> reviewStrategy(
        org.investpro.ai.local.grpc.generated.StrategyReviewRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReviewStrategyMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.StrategyRankingResponse> rankStrategies(
        org.investpro.ai.local.grpc.generated.StrategyRankingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRankStrategiesMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.BacktestReviewResponse> reviewBacktest(
        org.investpro.ai.local.grpc.generated.BacktestReviewRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReviewBacktestMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.RiskScoreResponse> scoreRisk(
        org.investpro.ai.local.grpc.generated.RiskScoreRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getScoreRiskMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse> detectAnomaly(
        org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDetectAnomalyMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_HEALTH = 0;
  private static final int METHODID_ANALYZE_SIGNAL = 1;
  private static final int METHODID_DETECT_REGIME = 2;
  private static final int METHODID_REVIEW_STRATEGY = 3;
  private static final int METHODID_RANK_STRATEGIES = 4;
  private static final int METHODID_REVIEW_BACKTEST = 5;
  private static final int METHODID_SCORE_RISK = 6;
  private static final int METHODID_DETECT_ANOMALY = 7;
  private static final int METHODID_STREAM_SIGNALS = 8;
  private static final int METHODID_STREAM_MARKET_STATE = 9;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_HEALTH:
          serviceImpl.health((org.investpro.ai.local.grpc.generated.HealthRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.HealthResponse>) responseObserver);
          break;
        case METHODID_ANALYZE_SIGNAL:
          serviceImpl.analyzeSignal((org.investpro.ai.local.grpc.generated.SignalReviewRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewResponse>) responseObserver);
          break;
        case METHODID_DETECT_REGIME:
          serviceImpl.detectRegime((org.investpro.ai.local.grpc.generated.RegimeDetectionRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionResponse>) responseObserver);
          break;
        case METHODID_REVIEW_STRATEGY:
          serviceImpl.reviewStrategy((org.investpro.ai.local.grpc.generated.StrategyReviewRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.StrategyReviewResponse>) responseObserver);
          break;
        case METHODID_RANK_STRATEGIES:
          serviceImpl.rankStrategies((org.investpro.ai.local.grpc.generated.StrategyRankingRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.StrategyRankingResponse>) responseObserver);
          break;
        case METHODID_REVIEW_BACKTEST:
          serviceImpl.reviewBacktest((org.investpro.ai.local.grpc.generated.BacktestReviewRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.BacktestReviewResponse>) responseObserver);
          break;
        case METHODID_SCORE_RISK:
          serviceImpl.scoreRisk((org.investpro.ai.local.grpc.generated.RiskScoreRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RiskScoreResponse>) responseObserver);
          break;
        case METHODID_DETECT_ANOMALY:
          serviceImpl.detectAnomaly((org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest) request,
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_STREAM_SIGNALS:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.streamSignals(
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.SignalReviewResponse>) responseObserver);
        case METHODID_STREAM_MARKET_STATE:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.streamMarketState(
              (io.grpc.stub.StreamObserver<org.investpro.ai.local.grpc.generated.RegimeDetectionResponse>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getHealthMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.HealthRequest,
              org.investpro.ai.local.grpc.generated.HealthResponse>(
                service, METHODID_HEALTH)))
        .addMethod(
          getAnalyzeSignalMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.SignalReviewRequest,
              org.investpro.ai.local.grpc.generated.SignalReviewResponse>(
                service, METHODID_ANALYZE_SIGNAL)))
        .addMethod(
          getDetectRegimeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.RegimeDetectionRequest,
              org.investpro.ai.local.grpc.generated.RegimeDetectionResponse>(
                service, METHODID_DETECT_REGIME)))
        .addMethod(
          getReviewStrategyMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.StrategyReviewRequest,
              org.investpro.ai.local.grpc.generated.StrategyReviewResponse>(
                service, METHODID_REVIEW_STRATEGY)))
        .addMethod(
          getRankStrategiesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.StrategyRankingRequest,
              org.investpro.ai.local.grpc.generated.StrategyRankingResponse>(
                service, METHODID_RANK_STRATEGIES)))
        .addMethod(
          getReviewBacktestMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.BacktestReviewRequest,
              org.investpro.ai.local.grpc.generated.BacktestReviewResponse>(
                service, METHODID_REVIEW_BACKTEST)))
        .addMethod(
          getScoreRiskMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.RiskScoreRequest,
              org.investpro.ai.local.grpc.generated.RiskScoreResponse>(
                service, METHODID_SCORE_RISK)))
        .addMethod(
          getDetectAnomalyMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.AnomalyDetectionRequest,
              org.investpro.ai.local.grpc.generated.AnomalyDetectionResponse>(
                service, METHODID_DETECT_ANOMALY)))
        .addMethod(
          getStreamSignalsMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.SignalReviewRequest,
              org.investpro.ai.local.grpc.generated.SignalReviewResponse>(
                service, METHODID_STREAM_SIGNALS)))
        .addMethod(
          getStreamMarketStateMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              org.investpro.ai.local.grpc.generated.RegimeDetectionRequest,
              org.investpro.ai.local.grpc.generated.RegimeDetectionResponse>(
                service, METHODID_STREAM_MARKET_STATE)))
        .build();
  }

  private static abstract class InvestProAiServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    InvestProAiServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return org.investpro.ai.local.grpc.generated.InvestProAiProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("InvestProAiService");
    }
  }

  private static final class InvestProAiServiceFileDescriptorSupplier
      extends InvestProAiServiceBaseDescriptorSupplier {
    InvestProAiServiceFileDescriptorSupplier() {}
  }

  private static final class InvestProAiServiceMethodDescriptorSupplier
      extends InvestProAiServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    InvestProAiServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (InvestProAiServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new InvestProAiServiceFileDescriptorSupplier())
              .addMethod(getHealthMethod())
              .addMethod(getAnalyzeSignalMethod())
              .addMethod(getDetectRegimeMethod())
              .addMethod(getReviewStrategyMethod())
              .addMethod(getRankStrategiesMethod())
              .addMethod(getReviewBacktestMethod())
              .addMethod(getScoreRiskMethod())
              .addMethod(getDetectAnomalyMethod())
              .addMethod(getStreamSignalsMethod())
              .addMethod(getStreamMarketStateMethod())
              .build();
        }
      }
    }
    return result;
  }
}
