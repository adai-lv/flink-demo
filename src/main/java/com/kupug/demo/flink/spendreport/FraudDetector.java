package com.kupug.demo.flink.spendreport;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.walkthrough.common.entity.Alert;
import org.apache.flink.walkthrough.common.entity.Transaction;

import java.io.IOException;
import java.util.Objects;

/**
 * <p>
 * 欺诈侦查逻辑实现
 * </p>
 *
 * @author MaoHai.LV
 * @since 1.0
 */
public class FraudDetector extends KeyedProcessFunction<Long, Transaction, Alert> {

    private static final long serialVersionUID = 7886108298053805991L;

    private static final double SMALL_AMOUNT = 1.00;
    private static final double LARGE_AMOUNT = 500.00;
    private static final long ONE_MINUTE = 60 * 1000;

    private transient ValueState<Boolean> flagState;
    private transient ValueState<Long> timerState;

    @Override
    public void open(Configuration parameters) {
        ValueStateDescriptor<Boolean> flagDescriptor = new ValueStateDescriptor<>("flag", Types.BOOLEAN);
        flagState = getRuntimeContext().getState(flagDescriptor);

        ValueStateDescriptor<Long> timerDescriptor = new ValueStateDescriptor<>("timer", Types.LONG);
        timerState = getRuntimeContext().getState(timerDescriptor);
    }

    @Override
    public void processElement(Transaction value, Context ctx, Collector<Alert> out) throws Exception {

        Boolean lastTransactionWasSmall = flagState.value();
        System.out.println(value);

        if (Objects.nonNull(lastTransactionWasSmall)) {
            if (value.getAmount() > LARGE_AMOUNT) {
                Alert alert = new Alert();
                alert.setId(value.getAccountId());

                out.collect(alert);
            }

            cleanup(ctx);
        }

        if (value.getAmount() < SMALL_AMOUNT) {
            flagState.update(true);

            long timer = ctx.timerService().currentProcessingTime() + ONE_MINUTE;
            ctx.timerService().registerProcessingTimeTimer(timer);
            timerState.update(timer);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Alert> out) {
        timerState.clear();
        flagState.clear();
    }

    private void cleanup(Context ctx) throws IOException {
        Long timer = timerState.value();
        ctx.timerService().deleteProcessingTimeTimer(timer);

        timerState.clear();
        flagState.clear();
    }
}
