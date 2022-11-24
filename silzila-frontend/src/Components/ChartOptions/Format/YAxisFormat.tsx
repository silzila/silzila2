import React from "react";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import { updateFormatOption } from "../../../redux/ChartPoperties/ChartControlsActions";
import {
	ChartControl,
	ChartControlStateProps,
	ChartConYAxisFormats,
} from "../../../redux/ChartPoperties/ChartControlsInterface";
import {
	TabTileStateProps,
	TabTileStateProps2,
} from "../../../redux/TabTile/TabTilePropsInterfaces";
import InputPositiveNumber from "../CommonFunctions/InputPositiveNumber";
import { ArrayWithValueAndType } from "../CommonInterfaces";

const YAxisFormat = ({
	//props
	chartType,
	// state
	tabTileProps,
	chartControl,

	// dispatch
	updateFormat,
}: {
	chartType: string;
	tabTileProps: TabTileStateProps;
	chartControl: ChartControl;
	updateFormat: (
		propKey: string | number,
		formatType: string | any,
		option: string,
		value: any
	) => void;
}) => {
	var propKey: string = `${tabTileProps.selectedTabId}.${tabTileProps.selectedTileId}`;
	let formatObject: ChartConYAxisFormats =
		chartControl.properties[propKey].formatOptions.yAxisFormats;

	const separatorOptions: ArrayWithValueAndType[] = [
		{ type: "None", value: "None" },
		{ type: "Comma", value: "Comma" },
		{ type: "Abbrev", value: "Abbrev" },
	];

	const renderSeparatorOptions = () => {
		return separatorOptions.map(item => {
			return (
				<div
					key={item.value}
					className={
						item.value === formatObject.numberSeparator
							? "radioButtonSelected"
							: "radioButton"
					}
					// value={formatObject.numberSeparator}
					onClick={() => {
						updateFormat(propKey, "yAxisFormats", "numberSeparator", item.value);
					}}
				>
					{item.type}
				</div>
			);
		});
	};

	return (
		<React.Fragment>
			<div className="optionDescription">
				{chartType === "scatterPlot" ? "Y-AXIS MEASURE FORMAT" : "MEASURE AXIS FORMAT"}{" "}
			</div>

			<div className="optionDescription">
				<label htmlFor="enableDisable" className="enableDisableLabel">
					Separator
				</label>
			</div>
			<div className="radioButtons" style={{ padding: "0", margin: "auto auto 0.5rem" }}>
				{renderSeparatorOptions()}
			</div>
			<div className="optionDescription">
				<label htmlFor="enableDisable" className="enableDisableLabel">
					Round Off
				</label>
			</div>
			<div className="optionDescription">
				<input
					type="checkbox"
					id="enableDisable"
					checked={formatObject.enableRounding}
					onChange={() => {
						updateFormat(
							propKey,
							"yAxisFormats",
							"enableRounding",
							!formatObject.enableRounding
						);
					}}
				/>
				<InputPositiveNumber
					value={formatObject.roundingDigits}
					updateValue={(value: number) => {
						if (value >= 0) {
							updateFormat(propKey, "yAxisFormats", "roundingDigits", value);
						} else {
							updateFormat(propKey, "yAxisFormats", "roundingDigits", 0);
						}
					}}
					disabled={formatObject.enableRounding ? false : true}
				/>
				<span style={{ margin: "auto 0px" }}>decimal</span>
			</div>
		</React.Fragment>
	);
};

const mapStateToProps = (state: ChartControlStateProps & TabTileStateProps2) => {
	return {
		chartControl: state.chartControls,
		tabTileProps: state.tabTileProps,
	};
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
	return {
		updateFormat: (
			propKey: string | number,
			formatType: string | number,
			option: string,
			value: any
		) => dispatch(updateFormatOption(propKey, formatType, option, value)),
	};
};

export default connect(mapStateToProps, mapDispatchToProps)(YAxisFormat);
