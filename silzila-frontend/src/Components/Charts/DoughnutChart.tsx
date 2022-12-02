import ReactEcharts from "echarts-for-react";
import { useEffect, useState } from "react";
import { connect } from "react-redux";
import { ChartControlsProps } from "../../redux/ChartPoperties/ChartControlsInterface";
import { formatChartLabelValue } from "../ChartOptions/Format/NumberFormatter";
import {
	ChartsMapStateToProps,
	ChartsReduxStateProps,
	FormatterValueProps,
} from "./ChartsCommonInterfaces";

const DoughnutChart = ({
	//props
	propKey,
	graphDimension,
	chartArea,
	graphTileSize,

	//state
	chartProperties,
	chartControls,
}: ChartsReduxStateProps) => {
	// TODO: conde breaks when apply filters
	var chartControl: ChartControlsProps = chartControls.properties[propKey];
	let chartData: any[] = chartControl.chartData ? chartControl.chartData : [];

	const [chartDataKeys, setchartDataKeys] = useState<string[]>([]);

	useEffect(() => {
		if (chartControl.chartData.length >= 1) {
			setchartDataKeys(Object.keys(chartData[0]));
			var objKey: string;
			if (chartProperties.properties[propKey].chartAxes[1].fields[0]) {
				if ("time_grain" in chartProperties.properties[propKey].chartAxes[1].fields[0]) {
					objKey =
						chartProperties.properties[propKey].chartAxes[1].fields[0].fieldname +
						"__" +
						chartProperties.properties[propKey].chartAxes[1].fields[0].time_grain;
				} else {
					objKey = chartProperties.properties[propKey].chartAxes[1].fields[0].fieldname;
				}
				chartControl.chartData.map((el: any) => {
					if (objKey in el) {
						let agg = el[objKey];
						//console.log(agg);
						if (agg) el[objKey] = agg.toString();
					}
					return el;
				});
			}
		}
	}, [chartData, chartControl]);

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
						margin: "auto",
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
								startAngle: chartControl.axisOptions.pieAxisOptions.pieStartAngle,
								clockwise: chartControl.axisOptions.pieAxisOptions.clockWise,

								label: {
									position: chartControl.labelOptions.pieLabel.labelPosition,
									show: chartControl.labelOptions.showLabel,
									fontSize: chartControl.labelOptions.fontSize,
									color: chartControl.labelOptions.labelColorManual
										? chartControl.labelOptions.labelColor
										: null,
									padding: [
										chartControl.labelOptions.pieLabel.labelPadding,
										chartControl.labelOptions.pieLabel.labelPadding,
										chartControl.labelOptions.pieLabel.labelPadding,
										chartControl.labelOptions.pieLabel.labelPadding,
									],

									formatter: (value: FormatterValueProps) => {
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
	return <>{chartData.length >= 1 ? <RenderChart /> : ""}</>;
};
const mapStateToProps = (state: ChartsMapStateToProps, ownProps: any) => {
	return {
		chartProperties: state.chartProperties,
		chartControls: state.chartControls,
	};
};

export default connect(mapStateToProps, null)(DoughnutChart);
