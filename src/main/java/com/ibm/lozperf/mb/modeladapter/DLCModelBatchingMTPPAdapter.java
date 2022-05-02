package com.ibm.lozperf.mb.modeladapter;

import java.util.List;

import com.ibm.lozperf.mb.Inputs;
import com.ibm.lozperf.mb.ModelAdapter;
import com.ibm.lozperf.mb.batching.BatchCollector;
import com.ibm.lozperf.mb.batching.Job;
import com.ibm.onnxmlir.OMModel;
import com.ibm.onnxmlir.OMTensor;
import com.ibm.onnxmlir.OMTensorList;

public class DLCModelBatchingMTPPAdapter implements ModelAdapter {

	protected BatchCollector<DlcInput> batchCollector = new BatchCollector<>(this::batchPredict);

	@Override
	public void close() throws Exception {
		batchCollector.close();
	}

	protected void batchPredict(List<Job<DlcInput>> batch) {

		final int nTS = numberTimesteps();
		final int inpLenght = batch.size() * nTS;
		float[] amounts = new float[inpLenght];
		int[] days = new int[inpLenght];
		int[] hours = new int[inpLenght];
		long[] mccs = new long[inpLenght];
		long[] cities = new long[inpLenght];
		long[] names = new long[inpLenght];
		long[] states = new long[inpLenght];
		int[] minutes = new int[inpLenght];
		int[] months = new int[inpLenght];
		long[] timeDeltas = new long[inpLenght];
		long[] useChip = new long[inpLenght];
		long[] zips = new long[inpLenght];

		long[] shape = { batch.size(), nTS };

		for (int i = 0, base = 0; i < batch.size(); i++, base += nTS) {
			DlcInput inp = batch.get(i).getInput();
			System.arraycopy(inp.amounts, 0, amounts, base, inp.amounts.length);
			System.arraycopy(inp.days, 0, days, base, inp.days.length);
			System.arraycopy(inp.hours, 0, hours, base, inp.hours.length);
			System.arraycopy(inp.mccs, 0, mccs, base, inp.mccs.length);
			System.arraycopy(inp.cities, 0, cities, base, inp.cities.length);
			System.arraycopy(inp.names, 0, names, base, inp.names.length);
			System.arraycopy(inp.states, 0, states, base, inp.states.length);
			System.arraycopy(inp.minutes, 0, minutes, base, inp.minutes.length);
			System.arraycopy(inp.months, 0, months, base, inp.months.length);
			System.arraycopy(inp.timeDeltas, 0, timeDeltas, base, inp.timeDeltas.length);
			System.arraycopy(inp.useChip, 0, useChip, base, inp.useChip.length);
			System.arraycopy(inp.zips, 0, zips, base, inp.zips.length);
		}

		OMTensorList tensorList = new OMTensorList(
				new OMTensor[] { new OMTensor(amounts, shape), new OMTensor(days, shape), new OMTensor(hours, shape),
						new OMTensor(mccs, shape), new OMTensor(cities, shape), new OMTensor(names, shape),
						new OMTensor(states, shape), new OMTensor(minutes, shape), new OMTensor(months, shape),
						new OMTensor(timeDeltas, shape), new OMTensor(useChip, shape), new OMTensor(zips, shape) });
		tensorList = OMModel.mainGraph(tensorList);

		float[] results = tensorList.getOmtByIndex(0).getFloatData();
		for (int i = 0; i < batch.size(); i++) {
			batch.get(i).setResult(results[i] > 0.5);
		}
	}

	private ThreadLocal<DlcInput> tlDlcInput = ThreadLocal.withInitial(() -> new DlcInput());

	@Override
	public boolean checkFraud(Inputs inputs) {
		DlcInput dlcInputs = tlDlcInput.get();

		for (int i = 0; i < inputs.Amount[0].length; i++)
			dlcInputs.amounts[i] = inputs.Amount[0][i].floatValue();
		
		dlcInputs.minutes = inputs.Minute[0];
		dlcInputs.hours = inputs.Hour[0];
		dlcInputs.days = inputs.Day[0];
		dlcInputs.months = inputs.Hour[0];	
		dlcInputs.timeDeltas = inputs.TimeDelta[0];
		dlcInputs.useChip = inputs.UseChip[0];
		
		DLCModelBatchingAdapter.map(inputs.MCC[0], DLCModelBatchingAdapter.mccMap, dlcInputs.mccs, 0);
		DLCModelBatchingAdapter.map(inputs.MerchantCity[0], DLCModelBatchingAdapter.cityMap, dlcInputs.cities, 0);
		DLCModelBatchingAdapter.map(inputs.MerchantName[0], DLCModelBatchingAdapter.nameMap, dlcInputs.names, 0);
		DLCModelBatchingAdapter.map(inputs.MerchantState[0], DLCModelBatchingAdapter.stateMap, dlcInputs.states, 0);
		DLCModelBatchingAdapter.map(inputs.Zip[0], DLCModelBatchingAdapter.zipMap, dlcInputs.zips, 0);

		return batchCollector.predict(dlcInputs);
	}

	private class DlcInput {
		final int inpLenghtnTimesteps = numberTimesteps();
		float[] amounts = new float[inpLenghtnTimesteps];
		int[] days;
		int[] hours;
		long[] mccs = new long[inpLenghtnTimesteps];
		long[] cities = new long[inpLenghtnTimesteps];
		long[] names = new long[inpLenghtnTimesteps];
		long[] states = new long[inpLenghtnTimesteps];
		int[] minutes;
		int[] months;
		long[] timeDeltas = new long[inpLenghtnTimesteps];
		long[] useChip;
		long[] zips = new long[inpLenghtnTimesteps];
	}
}
