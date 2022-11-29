import ReactEcharts from "echarts-for-react";
import { useEffect, useState } from "react";
import { connect } from "react-redux";
import { ChartControlsProps } from "../../redux/ChartPoperties/ChartControlsInterface";
import { ColorSchemes } from "../ChartOptions/Color/ColorScheme";
import { ChartsMapStateToProps, ChartsReduxStateProps } from "./ChartsCommonInterfaces";

const Sankey = ({
	// props
	propKey,
	graphDimension,
	chartArea,
	graphTileSize,

	//state
	chartControls,
	chartProperties,
}: ChartsReduxStateProps) => {
	var chartControl: ChartControlsProps = chartControls.properties[propKey];
	var colorSchems: string[] = ColorSchemes[6].colors;

	let chartData: any = chartControl.chartData ? chartControl.chartData.result : "";

	const [nodes, setNodes] = useState<any>([]);
	const [links, setLinks] = useState<any>([]);

	var dimensionsKeys: string[] | any = [];
	var measure: string = "";

	useEffect(() => {
		if (chartData) {
			console.log(chartData);

			console.log();

			dimensionsKeys = chartProperties.properties[propKey].chartAxes[1].fields.map(el => {
				return el.fieldname;
			});
			console.log(dimensionsKeys);

			//getting measure value as string since allowed numof measure is 1 for this chart

			chartProperties.properties[propKey].chartAxes[2].fields.map(el => {
				measure = `${el.fieldname}__${el.agg}`;
			});

			const getColorOfNode = (nodeName: string) => {
				console.log(nodeName);
				var color = "";
				chartControl.sankeyControls.nodesAndColors.map(el => {
					if (el.nodeName === nodeName) {
						color = el.nodeColor;
					}
				});
				console.log(color);
				return color;
			};

			//getting values for data in series
			var finalValuesOfNode: any = [];
			dimensionsKeys.forEach((element: any, i: number) => {
				var allValues = chartData.map((dt: any) => dt[element]);
				// TODO: getting error in uniqueValues
				// var uniqueValues = [...new Set(allValues)];

				// uniqueValues = uniqueValues.map(el => {
				// 	return {
				// 		name: el,
				// 		label: {
				// 			position:
				// 				chartControl.sankeyControls.labelPosition === "inside"
				// 					? i / 2 === 0
				// 						? [30 + chartControl.sankeyControls.labelDistance, 50]
				// 						: [-8 - chartControl.sankeyControls.labelDistance, 50]
				// 					: i / 2 === 0
				// 					? [-8 - chartControl.sankeyControls.labelDistance, 50]
				// 					: [30 + chartControl.sankeyControls.labelDistance, 50],
				// 			show: chartControl.labelOptions.showLabel,
				// 			fontSize: chartControl.labelOptions.fontSize,
				// 			color: chartControl.labelOptions.labelColorManual
				// 				? chartControl.labelOptions.labelColor
				// 				: "black",

				// 			overflow: chartControl.sankeyControls.overFlow,
				// 			distance: chartControl.sankeyControls.labelDistance,
				// 			rotate: chartControl.sankeyControls.labelRotate,
				// 		},
				// 		itemStyle: {
				// 			color:
				// 				chartControl.sankeyControls.nodesAndColors.length !== 0
				// 					? getColorOfNode(element)
				// 					: colorSchems[i],
				// 		},
				// 	};
				// });

				// finalValuesOfNode.push(...uniqueValues);
			});

			// finalValuesOfNode = finalValuesOfNode.map(el => {
			// 	return {
			// 		name: el,
			// 		label: {
			// 			position: chartControl.sankeyControls.labelPosition,
			// 			show: chartControl.labelOptions.showLabel,
			// 			fontSize: chartControl.labelOptions.fontSize,
			// 			color: chartControl.labelOptions.labelColorManual
			// 				? chartControl.labelOptions.labelColor
			// 				: "black",
			// 			align: chartControl.sankeyControls.horizondalAlign,
			// 			verticalAlign: chartControl.sankeyControls.verticalAlign,
			// 			overflow: chartControl.sankeyControls.overFlow,
			// 			distance: chartControl.sankeyControls.labelDistance,
			// 			rotate: chartControl.sankeyControls.labelRotate,
			// 		},
			// 		itemStyle: {
			// 			color:
			// 				chartControl.sankeyControls.nodesAndColors.length !== 0
			// 					? getColorOfNode(el)
			// 					: chartControl.sankeyControls.nodeColor,
			// 		},
			// 	};
			// });
			console.log(finalValuesOfNode);
			setNodes(finalValuesOfNode);

			//getting values for links in series
			var valuesOfLink = [];

			for (var i = 0; i < dimensionsKeys.length - 1; i++) {
				console.log(i);
				valuesOfLink = chartData.map((el: any) => {
					var obj: any = {};
					obj.source = el[dimensionsKeys[i]];
					obj.target = el[dimensionsKeys[i + 1]];
					obj.value = el[measure];
					obj.lineStyle = {
						color: chartControl.sankeyControls.linkColor,
						opacity: chartControl.sankeyControls.opacity / 100,
						curveness: chartControl.sankeyControls.curveness / 100,
					};
					return obj;
				});
			}

			console.log(valuesOfLink);
			setLinks(valuesOfLink);
		}
	}, [chartData, chartControl]);

	const RenderChart = () => {
		// console.log(nodes, links);
		return (
			<ReactEcharts
				opts={{ renderer: "svg" }}
				theme={chartControl.colorScheme}
				style={{
					padding: "5px",
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
				// 	series: [
				// 		{
				// 			type: "treemap",
				//
				// 			label: {
				// 				show: chartControl.labelOptions.showLabel,
				// 				fontSize: chartControl.labelOptions.fontSize,
				// 				color: chartControl.labelOptions.labelColorManual
				// 					? chartControl.labelOptions.labelColor
				// 					: null,
				// 				position: chartControl.treeMapChartControls.labelPosition,
				// 				rotate: chartControl.treeMapChartControls.labelRotate,
				// 				align: chartControl.treeMapChartControls.labelRotate,
				// 				verticalAlign: chartControl.treeMapChartControls.horizondalAlign,
				// 				overflow: chartControl.treeMapChartControls.overFlow,
				// 			},
				// 			itemStyle: {
				// 				borderWidth: chartControl.treeMapChartControls.borderWidth,
				// 				gapWidth: chartControl.treeMapChartControls.gapWidth,
				// 				borderColorSaturation: 1,
				// 			},
				// 			breadcrumb: {
				// 				show: chartControl.treeMapChartControls.showBreadCrumb,
				// 				height: chartControl.treeMapChartControls.bcHeight,
				// 				emptyItemWidth: chartControl.treeMapChartControls.bcWidth,
				// 				itemStyle: {
				// 					color: chartControl.treeMapChartControls.bcColor,
				// 				},
				// 			},
				// 			leafDepth: chartControl.treeMapChartControls.leafDepth,
				// 			data: sourceData,
				// 		},
				// 	],
				// }}

				option={{
					// title: {
					// 	text: "Sankey Diagram",
					// },
					tooltip: {
						show: chartControl.mouseOver.enable,
						trigger: "item",
						triggerOn: "mousemove",
					},
					series: [
						{
							type: "sankey",
							left: chartControl.chartMargin.left + "%",
							right: chartControl.chartMargin.right + "%",
							top: chartControl.chartMargin.top + "%",
							bottom: chartControl.chartMargin.bottom + "%",
							nodeWidth: chartControl.sankeyControls.nodeWidth,
							nodeGap: chartControl.sankeyControls.nodeGap,
							nodeAlign: chartControl.sankeyControls.nodeAlign,
							orient: chartControl.sankeyControls.orient,
							draggable: chartControl.sankeyControls.draggable,
							width:
								100 -
								(chartControl.chartMargin.left + chartControl.chartMargin.right) +
								"%",
							height:
								100 -
								(chartControl.chartMargin.top + chartControl.chartMargin.bottom) +
								"%",
							// label: {
							// 	show: chartControl.labelOptions.showLabel,
							// 	fontSize: chartControl.labelOptions.fontSize,
							// 	color: chartControl.labelOptions.labelColorManual
							// 		? chartControl.labelOptions.labelColor
							// 		: null,
							// 	align: chartControl.sankeyControls.horizondalAlign,
							// 	verticalAlign: chartControl.sankeyControls.verticalAlign,
							// 	overflow: chartControl.sankeyControls.overFlow,
							// 	distance: chartControl.sankeyControls.labelDistance,
							// 	rotate: chartControl.sankeyControls.labelRotate,
							// },
							data: nodes,
							links: links,
							emphasis: {
								focus: "adjacency",
							},
							levels: [
								{
									depth: 0,
									itemStyle: {
										color: "#fbb4ae",
									},
									lineStyle: {
										color: "source",
										opacity: 0.6,
									},
								},
								{
									depth: 1,
									itemStyle: {
										color: "#b3cde3",
									},
									lineStyle: {
										color: "source",
										opacity: 0.6,
									},
								},
								{
									depth: 2,
									itemStyle: {
										color: "#ccebc5",
									},
									lineStyle: {
										color: "source",
										opacity: 0.6,
									},
								},
								{
									depth: 3,
									itemStyle: {
										color: "#decbe4",
									},
									lineStyle: {
										color: "source",
										opacity: 0.6,
									},
								},
							],
							lineStyle: {
								curveness: 0.5,
							},
						},
					],
				}}
			/>
		);
	};

	return <>{chartData ? <RenderChart /> : <RenderChart />}</>;
};
const mapStateToProps = (state: ChartsMapStateToProps, ownProps: any) => {
	return {
		chartControlState: state.chartControls,
		chartProperties: state.chartProperties,
	};
};

export default connect(mapStateToProps, null)(Sankey);
