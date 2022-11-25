import ReactEcharts from "echarts-for-react";
import { useEffect, useState } from "react";
import { connect } from "react-redux";
import { ChartControlsProps } from "../../redux/ChartPoperties/ChartControlsInterface";
import { formatChartLabelValue } from "../ChartOptions/Format/NumberFormatter";
import { ChartsMapStateToProps, ChartsReduxStateProps } from "./ChartsCommonInterfaces";

const RoseChart = ({
	//props
	propKey,
	graphDimension,
	chartArea,
	graphTileSize,

	//state
	chartProperties,
	chartControls,
}: ChartsReduxStateProps) => {
	var chartControl: ChartControlsProps = chartControls.properties[propKey];
	let chartData = chartControl.chartData ? chartControl.chartData.result : "";
	const [chartDataKeys, setChartDataKeys] = useState<any>([]);

	useEffect(() => {
		if (chartControl.chartData !== "") {
			setChartDataKeys(Object.keys(chartData[0]));
			var objKey: any;
			if (chartProperties.properties[propKey].chartAxes[1].fields[0]) {
				if ("time_grain" in chartProperties.properties[propKey].chartAxes[1].fields[0]) {
					objKey =
						chartProperties.properties[propKey].chartAxes[1].fields[0].fieldname +
						"__" +
						chartProperties.properties[propKey].chartAxes[1].fields[0].time_grain;
				} else {
					objKey = chartProperties.properties[propKey].chartAxes[1].fields[0].fieldname;
				}

				chartControl.chartData.result.map((el: any) => {
					if (objKey in el) {
						let agg = el[objKey];
						//console.log(agg);
						if (agg) el[objKey] = agg.toString();
						else el[objKey] = "null";
					}
					return el;
				});
			}
		}
	}, [chartData, chartControl]);

	// //console.log(chartData);

	var seriesObj = {
		type: "pie",
		roseType: "area",
		label: {
			position: "outSide",
			show: chartControl.labelOptions.showLabel,
			fontSize: chartControl.labelOptions.fontSize,
			color: chartControl.labelOptions.labelColorManual
				? chartControl.labelOptions.labelColor
				: null,
		},
	};

	const RenderChart = () => {
		return (
			<>
				<ReactEcharts
					theme={chartControl.colorScheme}
					style={{
						padding: "1rem",
						width: graphDimension.width,
						height: graphDimension.height,
						overflow: "hidden",
						border: chartArea
							? "none"
							: graphTileSize
							? "none"
							: "1px solid rgb(238,238,238)",
					}}
					option={{
						animation: chartArea ? false : true,
						legend: {
							type: "scroll",
							show: chartControl.legendOptions?.showLegend,
							itemHeight: chartControl.legendOptions?.symbolHeight,
							itemWidth: chartControl.legendOptions?.symbolWidth,
							itemGap: chartControl.legendOptions?.itemGap,

							left: chartControl.legendOptions?.position?.left,
							top: chartControl.legendOptions?.position?.top,
							orient: chartControl.legendOptions?.orientation,
						},

						tooltip: { show: chartControl.mouseOver.enable },
						dataset: {
							dimensions: Object.keys(chartData[0]),
							source: chartData,
						},

						series: [
							{
								type: "pie",
								roseType: "area",
								startAngle: chartControl.axisOptions.pieAxisOptions.pieStartAngle,
								clockwise: chartControl.axisOptions.pieAxisOptions.clockWise,
								label: {
									position: chartControl.labelOptions.pieLabel.labelPosition,
									show: chartControl.labelOptions.showLabel,
									fontSize: chartControl.labelOptions.fontSize,
									color: chartControl.labelOptions.labelColor,
									padding: [
										chartControl.labelOptions.pieLabel.labelPadding,
										chartControl.labelOptions.pieLabel.labelPadding,
										chartControl.labelOptions.pieLabel.labelPadding,
										chartControl.labelOptions.pieLabel.labelPadding,
									],

									formatter: (value: any) => {
										if (chartDataKeys) {
											var formattedValue = value.value[chartDataKeys[1]];
											formattedValue = formatChartLabelValue(
												chartControl,
												formattedValue
											);
											return formattedValue;
										}
									},
								},
								itemStyle: {
									borderRadius: 5,
								},
								radius: [
									chartControl.chartMargin.innerRadius + "%",
									chartControl.chartMargin.outerRadius + "%",
								],
							},
						],
					}}
				/>
			</>
		);
	};
	return <>{chartData ? <RenderChart /> : ""}</>;
};

const mapStateToProps = (state: ChartsMapStateToProps, ownProps: any) => {
	return {
		chartProperties: state.chartProperties,
		chartControls: state.chartControls,
	};
};

export default connect(mapStateToProps, null)(RoseChart);
