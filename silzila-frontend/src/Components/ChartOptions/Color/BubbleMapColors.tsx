// This component list all color themes available for bubble Map charts

import {
  FormControl,
  MenuItem,
  Popover,
  Select,
  InputBase,
} from "@mui/material";
import React, { useEffect, useState } from "react";
import { ColorResult, SketchPicker } from "react-color";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import {
  addTableConditionalFormats,
  setAreaColorOptions,
  setColorScheme,
  switchAutotoManualinSteps,
  updateBoxPlotStyleOptions,
  updatecfObjectOptions1,
  updateGeoChartStyleOptions,
} from "../../../redux/ChartPoperties/ChartControlsActions";
import {
  ChartOptionsProps,
  ChartOptionsStateProps,
} from "../CommonInterfaceForChartOptions";

import SliderWithInput from "../SliderWithInput";
import SwitchWithInput from "../SwitchWithInput";

import { ColorSchemes, ColorSchemesProps } from "./ColorScheme";
import {
  ChartConGeoChartControls,
  ChartControlsProps,
} from "../../../redux/ChartPoperties/ChartControlsInterface";
import GradientComponent from "../TableChartControlComponents/GradientComponent";
import {
  displayName,
  fieldName,
  getLabelValues,
} from "../../CommonFunctions/CommonFunctions";
import LabelComponent from "../TableChartControlComponents/LabelComponent";
import {
  CustomBgColor,
  CustomFontAndBgColor,
  StyleButtons,
} from "../CommonComponents";
import {
  // bgColor,
  bgColors,
  // changeBgColors,
  // clearBgColors,
  dimensionName,
  onclickchange,
} from "../../Charts/GeoChart/BubbleMap";

const inputBaseStyle = {
  border: "2px solid rgba(224,224,224,1)",
  borderRadius: "3px",
  height: "20px",
  fontSize: "12px",
  padding: "0px 4px",
  color: "#a7a7a7",
};

interface ChartColorsActions {
  token: any;
  setColorScheme: (propKey: string, color: string) => void;
  switchAutotoManualinSteps: (propKey: string, value: any) => void;
  setAreaColorOptions: (propKey: string, option: string, value: any) => void;
  updateBoxPlotStyleOptions: (
    propKey: string,
    option: string,
    value: any
  ) => void;

  updateGeoChartStyleOptions: (
    propKey: string,
    option: string,
    value: any
  ) => void;
}
const BubbleMapColors = ({
  // state
  chartControls,
  tabTileProps,
  chartProperties,

  token,

  // dispatch
  setColorScheme,
  setAreaColorOptions,
  switchAutotoManualinSteps,
  updateBoxPlotStyleOptions,
  updateGeoChartStyleOptions,
}: ChartOptionsProps & ChartColorsActions) => {
  var propKey: string = `${tabTileProps.selectedTabId}.${tabTileProps.selectedTileId}`;
  const [selectedMenu, setSelectedMenu] = useState<string>(
    chartControls.properties[propKey].colorScheme
  );
  const [isColorPopoverOpen, setColorPopOverOpen] = useState<boolean>(false);

  var geoStyle: ChartConGeoChartControls =
    chartControls.properties[propKey].geoChartControls || {};
  // const [isColorPopoverOpen, setColorPopOverOpen] = useState(false);
  const [color, setColor] = useState<string>("");
  const [colorFieldName, setColorFieldName] = useState<string>("");

  const [bgColor, setBgColor] = useState<{ [key: string]: string }>(bgColors);

  const handleColorChangeComplete = (color: any) => {
    setBgColor((prevColors) => ({
      ...prevColors,
      [colorFieldName]: color.hex,
    }));
  };
  const handleColorSchemeChange = (newScheme: string) => {
    setSelectedMenu(newScheme);
    // changeBgColors(); // Adjust your bgColors based on the selected scheme
  };
  const [reRender, setreRender] = useState<boolean>(false);

  // let bgColor;
  // bgColor = chartControls.properties[
  //   propKey
  // ].tableConditionalFormats[0]?.value?.map((item: any) => {
  //   return item.backgroundColor;
  // });
  // console.log(bgColor);
  let _dimensionField = chartProperties.properties[propKey].chartAxes[1];
  let keyName = fieldName(_dimensionField.fields[0]);

  /////////////////////////////////////////////////////////////////////////////////////////////////

  let index = 0;
  let chartData: any[] = chartControls.properties[propKey].chartData || [];
  var chartThemes: any[] = ColorSchemes.filter((el) => {
    return el.name === chartControls.properties[propKey].colorScheme;
  });

  let _locationField = chartProperties.properties[propKey].chartAxes[2];
  // let _dimensionField = chartProperties.properties[propKey].chartAxes[1];
  let _measureField = chartProperties.properties[propKey].chartAxes[3];
  // let keyName = fieldName(_locationField.fields[0]);
  let dimName = displayName(_locationField.fields[0]);
  // let dimName = fieldName(_dimensionField.fields[0]);
  let valueName = fieldName(_measureField.fields[0]);

  const setBgColors = () => {
    /////////////////////////////////////////////////////////////////////////////////Ch Col//////////////////////////////////////////////////////////
    // geoStyle.bgCol = {};
    const hasNullValues = chartData.some((item) =>
      item.hasOwnProperty(keyName)
    );
    let mapData = chartData?.map((item) => {
      console.log(chartData);
      console.log(item[keyName]);
      console.log(keyName);
      if (index < chartThemes[0].colors.length) {
        // bgColor.push(index);
      }
      return {
        // name: matchingMapJSONArray.find(
        //   (match: any) => match.key === item[keyName]?.trim()
        // )?.name,
        name: item[dimName],
        value: item[valueName] || 0,
        key: item[dimName],
        dim: !chartData[0].hasOwnProperty(keyName) ? "" : item[keyName],
        // ? item[keyName]
        // : item[keyName] === null
        // ? "null"
        // : "",
        // size: bgColor?.find(
        //   (match: any) => match.name === item[keyName]?.trim()
        // )?.value,
      };
    });
    // if (changeBgColor) {
    //   setupdateBg(!updateBg);
    //   // bgColors = {};
    //   // geoStyle.bgCol = {};
    //   // geoStyle.bgCol = bgColors;
    //   // const newBgColors = { ...bgColors };
    //   // updateGeoChartStyleOptions(propKey, "bgCol", newBgColors);
    //   // updateGeoChartStyleOptions(propKey, "bgCol", bgColors);
    //   changeBgColor = false;
    // }
    // updateGeoChartStyleOptions(propKey, "bgCol", {});
    index = 0;
    mapData?.forEach((item) => {
      if (item.dim !== undefined && !geoStyle.bgCol[item.dim]) {
        if (index < chartThemes[0].colors.length) {
          index++;
          bgColors[item.dim] = chartThemes[0].colors[index - 1];
          // bgColors = JSON.parse(JSON.stringify(bgColors));
          const newBgColors = {
            ...bgColors,
            [item.dim]: chartThemes[0].colors[index - 1],
          };
          updateGeoChartStyleOptions(propKey, "bgCol", newBgColors);
          // updateGeoChartStyleOptions(propKey, "bgCol", bgColors);
          // geoStyle.bgCol[item.dim] = chartThemes[0].colors[index - 1];
          // return {
          // [item.dim]: chartThemes[0].colors[index - 1],
          // name: item.dim,
          // col: chartThemes[0].colors[index - 1],
          // };
        } else {
          index = 0;
          index++;
          // return {
          bgColors[item.dim] = chartThemes[0].colors[index - 1];
          // bgColors = JSON.parse(JSON.stringify(bgColors));
          const newBgColors = {
            ...bgColors,
            // ...geoStyle.bgCol,
            [item.dim]: chartThemes[0].colors[index - 1],
          };
          updateGeoChartStyleOptions(propKey, "bgCol", newBgColors);
          // const newBgColors = { ...bgColors };
          // updateGeoChartStyleOptions(propKey, "bgCol", newBgColors);
          // updateGeoChartStyleOptions(propKey, "bgCol", bgColors);
          // geoStyle.bgCol[item.dim] = chartThemes[0].colors[index - 1];
          // name: item.dim,
          // col: chartThemes[0].colors[index - 1],
          // };
        }
      }
    });
    // bgColor = bgColors;
    // geoStyle.bgCol = bgColors;
    // const newBgColors = { ...bgColors };
    // updateGeoChartStyleOptions(propKey, "bgCol", newBgColors);
    // updateGeoChartStyleOptions(propKey, "bgCol", bgColors);
    console.log(geoStyle.bgCol);
  };
  useEffect(() => {
    setBgColors();
  }, [chartData]);

  //////////////////////////////////////////////////////////////////////////////////////////////

  const resetSelection = (data_value: string) => {
    setSelectedMenu(data_value);
    setColorScheme(propKey, data_value);
  };

  const changeBgColor = (color_data: string) => {
    var chartThemes: any[] = ColorSchemes.filter((el) => {
      return el.name === color_data;
    });
    let index = 0;
    Object.keys(bgColors).forEach((key) => {
      console.log(chartThemes[0].colors);
      bgColors[key] =
        chartThemes[0].colors[index % chartThemes[0].colors.length];
      const newBgColors = {
        // ...bgColors,
        ...geoStyle.bgCol,
        [key]: chartThemes[0].colors[index % chartThemes[0].colors.length],
      };
      updateGeoChartStyleOptions(propKey, "bgCol", newBgColors);
      console.log(geoStyle.bgCol);
      index++;
    });
  };

  // useEffect(() => {
  //   // const onSelectValue = (index: any, value: any) => {
  //   //   bgColors[index] = value;
  //   // };
  //   updateGeoChartStyleOptions(propKey, "bgCol", bgColors);
  // }, [bgColors, color, ColorSchemes, isColorPopoverOpen, geoStyle, reRender]);

  if (!chartProperties.properties[propKey].chartAxes[1].fields[0]) {
    return (
      <>
        <div className="optionDescription">Min</div>
        <div className="optionDescription">
          <InputBase
            style={{ height: "1.25rem", width: "35%" }}
            value={geoStyle.minValue}
            onChange={(e: any) => {
              updateGeoChartStyleOptions(propKey, "minValue", e.target.value);
            }}
            sx={inputBaseStyle}
            placeholder={"Enter Min Value"}
          />

          <div
            style={{
              height: "1.25rem",
              width: "50%",
              marginLeft: "20px",
              backgroundColor: geoStyle.minColor,
              color: geoStyle.minColor,
              border: "2px solid darkgray",
              margin: "auto",
            }}
            onClick={() => {
              setColor(geoStyle.minColor);
              setColorFieldName("minColor");
              setColorPopOverOpen(!isColorPopoverOpen);
            }}
          >
            {"  "}
          </div>
        </div>
        <div className="optionDescription">Max</div>
        <div className="optionDescription">
          <InputBase
            style={{ height: "1.25rem", width: "35%" }}
            value={geoStyle.maxValue}
            onChange={(e: any) => {
              updateGeoChartStyleOptions(propKey, "maxValue", e.target.value);
            }}
            sx={inputBaseStyle}
            placeholder={"Enter Max Value"}
          />

          <div
            style={{
              height: "1.25rem",
              width: "50%",
              marginLeft: "20px",
              backgroundColor: geoStyle.maxColor,
              color: geoStyle.maxColor,
              border: "2px solid darkgray",
              margin: "auto",
            }}
            onClick={() => {
              // console.log(geoStyle.maxColor);
              setColor(geoStyle.maxColor);
              setColorFieldName("maxColor");
              setColorPopOverOpen(!isColorPopoverOpen);
            }}
          >
            {"  "}
          </div>
        </div>
        <Popover
          open={isColorPopoverOpen}
          onClose={() => setColorPopOverOpen(false)}
          onClick={() => setColorPopOverOpen(false)}
          anchorReference="anchorPosition"
          anchorPosition={{ top: 350, left: 1300 }}
        >
          <div>
            <SketchPicker
              color={color}
              className="sketchPicker"
              width="16rem"
              onChangeComplete={(color) => {
                updateGeoChartStyleOptions(propKey, colorFieldName, color.hex);
              }}
              // onChange={(color) =>
              //   updateGeoChartStyleOptions(propKey, colorFieldName, color.hex)
              // }
              // disableAlpha
            />
          </div>
        </Popover>
      </>
    );
  } else {
    return (
      <div className="optionsInfo">
        <div className="optionDescription">COLOR SCHEME:</div>
        <FormControl
          fullWidth
          size="small"
          style={{ fontSize: "12px", borderRadius: "4px" }}
        >
          <Select
            size="small"
            value={selectedMenu}
            variant="outlined"
            onChange={(e) => {
              // changeBgColors();
              setreRender(!reRender);
              // const newScheme = e.target.value as string;
              // handleColorSchemeChange(newScheme);
              resetSelection(e.target.value);
              changeBgColor(e.target.value);
              // console.log(e);
              updateGeoChartStyleOptions(propKey, "bgCol", bgColors);
              console.log(geoStyle.bgCol);
              // clearBgColors();
            }}
            sx={{ fontSize: "14px", margin: "0 1rem" }}
          >
            {ColorSchemes.map((item: ColorSchemesProps) => {
              return (
                <MenuItem
                  value={item.name}
                  key={item.name}
                  sx={{
                    padding: "2px 10px",
                  }}
                >
                  <div
                    className="custom-option"
                    style={{
                      backgroundColor: item.background,
                      color: item.dark ? "white" : "#3b3b3b",
                    }}
                  >
                    <span className="color-name">{item.name}</span>
                    <div className="color-palette">
                      {item.colors.map((color: string) => {
                        return (
                          <div
                            className="indi-color"
                            style={{
                              height: "8px",
                              background: color,
                            }}
                            key={`${item.name}_${color}`}
                          ></div>
                        );
                      })}
                    </div>
                  </div>
                </MenuItem>
              );
            })}
          </Select>
        </FormControl>
        {/* <Popover
          open={isColorPopoverOpen}
          onClose={() => setColorPopOverOpen(false)}
          onClick={() => setColorPopOverOpen(false)}
          anchorReference="anchorPosition"
          anchorPosition={{ top: 350, left: 1300 }}
        >
          <div>
            <SketchPicker
              color={chartControls.properties[propKey].areaBackgroundColor}
              className="sketchPicker"
              width="16rem"
              // styles={{ padding: "0" }}
              onChangeComplete={(color: ColorResult) => {
                setAreaColorOptions(propKey, "areaBackgroundColor", color.hex);
              }}
              onChange={(color: ColorResult) =>
                setAreaColorOptions(propKey, "areaBackgroundColor", color.hex)
              }
              disableAlpha
            />
          </div>
        </Popover> */}

        <div
          className="optionDescription"
          style={{ display: "flex", flexDirection: "column", marginTop: "0px" }}
        >
          {Object.keys(geoStyle.bgCol).map((item: any, index: any) => {
            if (item !== "") {
              return (
                <>
                  <div
                    style={{
                      display: "flex",
                      flexDirection: "row",
                      padding: "4px",
                    }}
                  >
                    <CustomBgColor
                      id={item}
                      backgroundColor={geoStyle.bgCol[item]}
                      onChangeColorProps={(
                        option: string,
                        value: any,
                        columnValue: string
                      ) => {
                        bgColors[item] = value;
                        console.log(bgColors);
                        // geoStyle.bgCol[item] = value;
                        // setColor(bgColors[item]);
                        const newBgColors = {
                          ...geoStyle.bgCol,
                          [item]: value,
                        };
                        updateGeoChartStyleOptions(
                          propKey,
                          "bgCol",
                          newBgColors
                        );
                        // updateGeoChartStyleOptions(propKey, "bgCol", bgColors);
                        onclickchange();
                      }}
                      fontColor="white"
                    />
                    <div style={{ paddingLeft: "2px" }}>
                      {keyName}
                      {" : "}
                      {item}
                    </div>
                  </div>
                </>
              );
            }
          })}
        </div>
      </div>
    );
  }
};

const mapStateToProps = (state: ChartOptionsStateProps, ownProps: any) => {
  return {
    chartControls: state.chartControls,
    tabTileProps: state.tabTileProps,
    chartProperties: state.chartProperties,
  };
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
  return {
    setColorScheme: (propKey: string, color: string) =>
      dispatch(setColorScheme(propKey, color)),
    switchAutotoManualinSteps: (propKey: string, value: any) =>
      dispatch(switchAutotoManualinSteps(propKey, value)),
    setAreaColorOptions: (propKey: string, option: string, value: any) =>
      dispatch(setAreaColorOptions(propKey, option, value)),
    updateBoxPlotStyleOptions: (propKey: string, option: string, value: any) =>
      dispatch(updateBoxPlotStyleOptions(propKey, option, value)),
    updateGeoChartStyleOptions: (propKey: string, option: string, value: any) =>
      dispatch(updateGeoChartStyleOptions(propKey, option, value)),
    updatecfObjectOptions1: (propKey: string, item: any) =>
      dispatch(updatecfObjectOptions1(propKey, item)),
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(BubbleMapColors);
