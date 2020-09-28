/****************************************************************************
 *
 *   Copyright (c) 2017,2018 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.ui.widgets.tuning.vibration;

import com.comino.analysis.FFT;
import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.file.KeyFigurePreset;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.AnalysisDataModelMetaData;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.ui.widgets.charts.IChartControl;
import com.comino.flight.ui.widgets.charts.utils.XYDataPool;
import com.comino.jfx.extensions.ChartControlPane;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.model.DataModel;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;


public class Vibration extends VBox implements IChartControl  {

	private static final int POINTS = 512;


	@FXML
	private HBox hbox;

	@FXML
	private ProgressBar vx;

	@FXML
	private ProgressBar vy;

	@FXML
	private ProgressBar vz;

	@FXML
	private LineChart<Number, Number> fft;

	@FXML
	private NumberAxis xAxis;

	@FXML
	private NumberAxis yAxis;


	private Timeline timeline;

	private DataModel model;


	private FloatProperty   scroll       = new SimpleFloatProperty(0);
	private FloatProperty   replay       = new SimpleFloatProperty(0);

	private  XYChart.Series<Number,Number> series1;
	private  XYChart.Series<Number,Number> series2;
	private  XYChart.Series<Number,Number> series3;

	private float[]  data1 = new float[POINTS];
	private float[]  data2 = new float[POINTS];
	private float[]  data3 = new float[POINTS];


	private FFT fft1 = null;
	private FFT fft2 = null;
	private FFT fft3 = null;

	private XYDataPool pool = null;

	private AnalysisDataModelMetaData meta = AnalysisDataModelMetaData.getInstance();
	private AnalysisModelService      dataService = AnalysisModelService.getInstance();

	private int max_pt = 0;
	private int sample_rate = 50;

	public Vibration() {

		FXMLLoadHelper.load(this, "Vibration.fxml");
		timeline = new Timeline(new KeyFrame(Duration.millis(100), ae -> {
			vx.setProgress(model.vibration.vibx * 1e3);
			vy.setProgress(model.vibration.viby * 1e3);
			vz.setProgress(model.vibration.vibz * 1e3);

			if(dataService.isCollecting())
				updateGraph();

		}));
		timeline.setCycleCount(Timeline.INDEFINITE);
		timeline.setDelay(Duration.ZERO);

		fft1 = new FFT( POINTS, sample_rate );
		fft2 = new FFT( POINTS, sample_rate );
		fft3 = new FFT( POINTS, sample_rate );

		//		fft1.window(FFT.HAMMING);
		//		fft2.window(FFT.HAMMING);
		//		fft3.window(FFT.HAMMING);

		pool = new XYDataPool();


	}


	@FXML
	private void initialize() {

		vx.setProgress(0); vy.setProgress(0); vz.setProgress(0);

		series1 = new XYChart.Series<Number,Number>();
		fft.getData().add(series1);
		series2 = new XYChart.Series<Number,Number>();
		fft.getData().add(series2);
//		series3 = new XYChart.Series<Number,Number>();
//		fft.getData().add(series3);

		xAxis.setAutoRanging(false);
		xAxis.setLowerBound(0);
		xAxis.setUpperBound(sample_rate/2);
		
		series1.setName("AccX");
		series1.setName("AccY");
		
		fft.setLegendVisible(false);




	}

	public void setup(IMAVController control) {
		this.model = control.getCurrentModel();

		StateProperties.getInstance().getRecordingProperty().addListener((p,o,n) -> {
			if(n.intValue()>0)
				timeline.play();
			else {
				timeline.stop();
				vx.setProgress(0); vy.setProgress(0); vz.setProgress(0);
			}
		});
		ChartControlPane.addChart(9,this);

		scroll.addListener((v, ov, nv) -> {
			max_pt =  dataService.calculateX1IndexByFactor(nv.floatValue());	
			updateGraph();
		});

		replay.addListener((v, ov, nv) -> {		
			max_pt = dataService.calculateX1Index(nv.intValue());
			updateGraph();

		});

	}

	private void updateGraph() {

		Platform.runLater(() -> {

			AnalysisDataModel m =null;

			if(isDisabled())
				return;

			if(dataService.isCollecting())
				max_pt = dataService.calculateIndexByFactor(1);


			series1.getData().clear();
			series2.getData().clear();
//			series3.getData().clear();


			if(max_pt <= POINTS) {
				
		//		linechart.getData().add(series3);

				return;
			}


			int point_pt = 0;
			for(int i = max_pt - POINTS; i < max_pt; i++ ) {
				m = dataService.getModelList().get(i);
				data1[point_pt] = (float)m.getValue("ACCX");	
				data2[point_pt] = (float)m.getValue("ACCY");	
//				data3[point_pt] = (float)m.getValue("ACCZ");	
				point_pt++;
			}

			fft1.forward(data1); 
			fft2.forward(data2); 
//			fft3.forward(data3);

			for(int i = 0; i < fft1.specSize(); i++ ) {
				series1.getData().add(pool.checkOut(i * fft1.getBandWidth(),fft1.getSpectrum()[i]));
			}

			for(int i = 0; i < fft2.specSize(); i++ ) {
				series2.getData().add(pool.checkOut(i * fft2.getBandWidth(),fft2.getSpectrum()[i]));
			}
//
//			for(int i = 0; i < fft3.specSize(); i++ ) {
//				series3.getData().add(pool.checkOut(i * fft3.getBandWidth(),fft3.getSpectrum()[i]));
//			}
		});
	}


	@Override
	public IntegerProperty getTimeFrameProperty() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public FloatProperty getScrollProperty() {
		return scroll;
	}


	@Override
	public FloatProperty getReplayProperty() {
		return replay;
	}


	@Override
	public BooleanProperty getIsScrollingProperty() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void refreshChart() {
		max_pt = dataService.calculateIndexByFactor(1);
		fft.getData().clear();
		fft.getData().add(series1);
		fft.getData().add(series2);
		updateGraph();
	}


	@Override
	public KeyFigurePreset getKeyFigureSelection() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void setKeyFigureSelection(KeyFigurePreset preset) {
		// TODO Auto-generated method stub

	}

}
