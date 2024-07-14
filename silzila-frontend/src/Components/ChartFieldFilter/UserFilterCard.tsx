import { useState, useEffect } from "react";
import "../ChartAxes/Card.css";
import "./UserFilterCard.css";
import { useDrag, useDrop } from "react-dnd";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import { connect } from "react-redux";
import {
  Checkbox,
  Divider,
  Menu,
  MenuItem,
  Select,
  Tooltip,
  Typography,
} from "@mui/material";
import FormGroup from "@mui/material/FormGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import Switch from "@mui/material/Switch";
import { alpha, styled } from "@mui/material/styles";
import { green } from "@mui/material/colors";

import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import TextField from "@mui/material/TextField";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import {
  updateLeftFilterItem,
  editChartPropItem,
  revertAxes,
  sortAxes,
  updtateFilterExpandeCollapse,
} from "../../redux/ChartPoperties/ChartPropertiesActions";
import LoadingPopover from "../CommonFunctions/PopOverComponents/LoadingPopover";
import FetchData from "../ServerCall/FetchData";
import moment from "moment";

import DoneIcon from "@mui/icons-material/Done";

// import { UnCheckedIcon } from "material-ui/svg-icons/toggle/check-box-outline-blank";
// import { CheckedIcon } from "material-ui/svg-icons/toggle/check-box";

import { PatternCollectionType } from "./UserFilterCardInterface";
import { Dispatch } from "redux";
import { ChartPropertiesStateProps } from "../../redux/ChartPoperties/ChartPropertiesInterfaces";
import { isLoggedProps } from "../../redux/UserInfo/IsLoggedInterfaces";
import { TabTileStateProps2 } from "../../redux/TabTile/TabTilePropsInterfaces";
import { UserFilterCardProps } from "./UserFilterCardInterface";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import style from "react-syntax-highlighter/dist/esm/styles/hljs/a11y-dark";
import { color } from "echarts";

const UserFilterCard = ({
  propKey,
  field,
  bIndex,
  itemIndex,
  token,
  uID,

  // state
  chartProp,

  // dispatch
  updateLeftFilterItem,
  deleteDropZoneItems,
  sortAxes,
  revertAxes,
  updtateFilterExpandeCollapse,
}: UserFilterCardProps & any) => {
  field.dataType = field.dataType.toLowerCase();

  let currentChartAxesName = uID ? "chartAxes_" + uID : "chartAxes";
  let currentChartAxes = JSON.parse(
    JSON.stringify(chartProp.properties[propKey][currentChartAxesName])
  );

  const { uId, fieldname, displayname, dataType, tableId } = field;
  var isCollapsed: boolean = currentChartAxes[0].isCollapsed;

  useEffect(() => {
    var res = currentChartAxes[0].fields.map((el: any) => {
      el.isCollapsed = !currentChartAxes[0].isCollapsed;
      return el;
    });

    updtateFilterExpandeCollapse(propKey, bIndex, res, currentChartAxesName);
  }, [isCollapsed]);

  const originalIndex = currentChartAxes[bIndex].fields.findIndex(
    (item: any) => item.uId === uId
  );

  //const [showOptions, setShowOptions] = useState(false);
  const [loading, setLoading] = useState(false);
  let sliderRange = [0, 0];

  const [anchorEl, setAnchorEl] = useState(null);
  const open = Boolean(anchorEl);

  const withPatternCollections: PatternCollectionType[] = [
    { key: "beginsWith", value: "Start With" },
    { key: "endsWith", value: "Ends With" },
    { key: "contains", value: "Contains" },
    { key: "exactMatch", value: "Exact Match" },
  ];

  const datePatternCollections: PatternCollectionType[] = [
    { key: "year", value: "Year" },
    { key: "quarter", value: "Quarter" },
    { key: "month", value: "Month" },
    { key: "yearquarter", value: "Year Quarter" },
    { key: "yearmonth", value: "Year Month" },
    { key: "date", value: "Date" },
    { key: "dayofmonth", value: "Day Of Month" },
    { key: "dayofweek", value: "Day Of Week" },
  ];

  const datePatternSearchConditionCollections: PatternCollectionType[] = [
    { key: "year", value: "Year" },
    { key: "quarter", value: "Quarter" },
    { key: "month", value: "Month" },
    { key: "date", value: "Date" },
    { key: "dayofmonth", value: "Day Of Month" },
    { key: "dayofweek", value: "Day Of Week" },
  ];
  const equalPatternCollections: PatternCollectionType[] = [
    { key: "greaterThan", value: "> Greater than" },
    { key: "lessThan", value: "< Less than" },
    { key: "greaterThanOrEqualTo", value: ">= Greater than or Equal to" },
    { key: "lessThanOrEqualTo", value: "<= Less than or Equal to" },
    { key: "equalTo", value: "= Equal to" },
    { key: "notEqualTo", value: "<> Not Equal to" },
    { key: "between", value: ">= Between <=" },
  ];

  let filterFieldData = JSON.parse(JSON.stringify(field));

  /* Initialize vaiarble to default values */

  useEffect(() => {
    if (!filterFieldData.includeexclude)
      filterFieldData["includeexclude"] = "Include";

    if (filterFieldData && filterFieldData.dataType) {
      switch (filterFieldData.dataType) {
        case "decimal":
        case "integer":
          if (!filterFieldData.fieldtypeoption) {
            filterFieldData["fieldtypeoption"] = "Search Condition";
          }
          break;
        case "date":
        case "timestamp":
          if (!filterFieldData.fieldtypeoption) {
            filterFieldData["prefix"] = "year";
            filterFieldData["fieldtypeoption"] = "Pick List";
          }
          break;
        default:
          if (!filterFieldData.fieldtypeoption) {
            filterFieldData["fieldtypeoption"] = "Pick List";
          }
          break;
      }
    }

    if (filterFieldData.fieldtypeoption === "Search Condition") {
      if (dataType) {
        switch (filterFieldData.dataType) {
          case "decimal":
          case "integer":
            if (!filterFieldData.exprType) {
              filterFieldData["exprType"] = "greaterThan";
            }
            break;
          case "text":
            if (!filterFieldData.exprType) {
              filterFieldData["exprType"] = "beginsWith";
            }
            break;
          case "timestamp":
          case "date":
            if (!filterFieldData.exprType) {
              filterFieldData["prefix"] = "year";
              filterFieldData["exprType"] = "greaterThan";
            }
            break;
          default:
            if (!filterFieldData.exprType) {
              filterFieldData["exprType"] = "greaterThan";
            }
            break;
        }
      }
    } else {
      if (
        filterFieldData &&
        filterFieldData.dataType &&
        filterFieldData.dataType === "timestamp" &&
        !filterFieldData.prefix
      ) {
        filterFieldData["prefix"] = "year";
        // filterFieldData["exprTypeTillDate"] = false;
      }

      async function _preFetchData() {
        if (!filterFieldData.rawselectmembers) {
          setLoading(true);
          await GetPickListItems();
          setLoading(false);
        }
      }
      filterFieldData["exprTypeTillDate"] = false;
      filterFieldData["switchenable"] = "disabled";

      _preFetchData();
    }

    //updateUserFilterItem(propKey, 0, itemIndex, constructChartAxesFieldObject(), currentChartAxesName);
    // eslint-disable-next-line
  }, []);

  const updateUserFilterItem = (
    propKey: string,
    bIndex: number,
    itemIndex: number,
    field: any,
    axesName: string
  ) => {
    //if(chartProp.properties[propKey].droppedFieldChartAxesUID === uID){
    updateLeftFilterItem(propKey, bIndex, itemIndex, field, axesName);
    //}
  };

  // const updateUserFilterItemExpandCollapse = (propKey:string, bIndex:number, itemIndex:number, field:any, axesName:string) =>{
  // 	if(chartProp.properties[propKey].droppedFieldChartAxesUID === uID){
  // 		updateLeftFilterItem(propKey, bIndex, itemIndex, field, axesName);
  // 	}
  // }

  ///Fech Field data for Pick List
  const fetchFieldData = (type: string) => {
    let bodyData: any = {
      tableId: tableId,
      fieldName: displayname,
      dataType: dataType,
      filterOption: "allValues",
    };

    if (dataType === "timestamp" || dataType === "date") {
      bodyData["timeGrain"] = filterFieldData.prefix || "year";
    }

    return FetchData({
      requestType: "withData",
      method: "POST",
      url: `filter-options?dbconnectionid=${chartProp.properties[propKey].selectedDs.connectionId}&datasetid=${chartProp.properties[propKey].selectedDs.id}`,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      data: bodyData,
    });
  };

  ///To get filter type for service call
  const _getFilterType = () => {
    switch (dataType) {
      case "text":
        return "text_user_selection";
      case "decimal":
      case "integer":
        return filterFieldData.fieldtypeoption === "Search Condition"
          ? "number_search"
          : "number_user_selection";
      case "timestamp":
      case "date":
        return filterFieldData.fieldtypeoption === "Search Condition"
          ? "date_search"
          : "date_user_selection";

      default:
        return "text_user_selection";
    }
  };

  ///To fetch Pick list items
  const GetPickListItems = async () => {
    let result: any = await fetchFieldData(_getFilterType());

    if (result) {
      if (result.data && result.data.length > 0) {
        result = result.data.map(
          (item: any) => item[Object.keys(result.data[0])[0]]
        );
      }

      let tempResult = ["(All)", ...result];

      filterFieldData["rawselectmembers"] = [...tempResult];
      filterFieldData["userSelection"] = tempResult;
      updateUserFilterItem(
        propKey,
        0,
        itemIndex,
        constructChartAxesFieldObject(),
        currentChartAxesName
      );
    }
  };

  /// Properties and behaviour when a filter card is dragged
  const [, drag] = useDrag({
    item: {
      uId: uId,
      fieldname: fieldname,
      displayname: fieldname,
      dataType: dataType,
      tableId: tableId,
      // type: "card",
      bIndex,
      originalIndex,
    },
    type: "card",

    end: (dropResult, monitor) => {
      const { uId, bIndex, originalIndex } = monitor.getItem();

      const didDrop = monitor.didDrop();

      if (!didDrop) {
        revertAxes(propKey, bIndex, uId, originalIndex, currentChartAxesName);
      }
    },
  });

  // Properties and behaviours when another filter card is dropped over this card
  const [, drop] = useDrop({
    accept: "card",
    canDrop: () => false,
    collect: (monitor) => ({
      backgroundColor1: monitor.isOver({ shallow: true }) ? 1 : 0,
    }),
    hover({ uId: dragUId, bIndex: fromBIndex }: any) {
      if (fromBIndex === bIndex && dragUId !== uId) {
        sortAxes(propKey, bIndex, dragUId, uId, currentChartAxesName);
      }
    },
  });

  ///Pick list CB change

  const handleCBChange = (event: any) => {
    if (event.target.name.toString() === "(All)") {
      if (event.target.checked) {
        filterFieldData["userSelection"] = [
          ...filterFieldData.rawselectmembers,
        ];
        filterFieldData["exprTypeTillDate"] = false;
        filterFieldData["switchenable"] = "disabled";
      } else {
        // filterFieldData["switchenable"] = "enabled";
        filterFieldData["userSelection"] = [];
      }
    } else {
      filterFieldData["switchenable"] = "enabled";
      if (event.target.checked) {
        if (!isNaN(event.target.name) && isFinite(event.target.name)) {
          let _name = event.target.name;

          if (_name.includes(".")) {
            _name = parseFloat(event.target.name);
          } else {
            _name = parseInt(event.target.name);
          }

          if (_name) {
            filterFieldData.userSelection.push(_name);
          }
        } else {
          filterFieldData.userSelection.push(event.target.name);
        }
      } else {
        let idx = filterFieldData.userSelection.findIndex(
          (item: any) => item.toString() === event.target.name.toString()
        );
        filterFieldData.userSelection.splice(idx, 1);
      }
      if (!filterFieldData.userSelection.length) {
        filterFieldData["switchenable"] = "disabled";
        filterFieldData["exprTypeTillDate"] = false;
      }

      let AllIdx = filterFieldData.userSelection.findIndex(
        (item: any) => item.toString() === "(All)"
      );

      if (AllIdx >= 0) {
        filterFieldData.userSelection.splice(AllIdx, 1);
      }
    }
    updateUserFilterItem(
      propKey,
      0,
      itemIndex,
      constructChartAxesFieldObject(),
      currentChartAxesName
    );
  };

  ///Render Pick list card from raw select members
  const SelecPickListCard = () => {
    let _selectionMembers = null;

    if (filterFieldData && filterFieldData.rawselectmembers) {
      _selectionMembers = filterFieldData.rawselectmembers.map(
        (item: any, index: number) => {
          return (
            <label className="UserFilterCheckboxes" key={index}>
              {filterFieldData.includeexclude === "Include" ? (
                <Checkbox
                  checked={
                    filterFieldData.userSelection
                      ? filterFieldData.includeexclude === "Include"
                        ? filterFieldData.userSelection.includes(item)
                          ? true
                          : false
                        : false
                      : false
                  }
                  // indeterminate={
                  // 	filterFieldData.userSelection
                  // 		? filterFieldData.includeexclude === "Exclude"
                  // 			? filterFieldData.userSelection.includes(item)
                  // 				? true
                  // 				: false
                  // 			: false
                  // 		: false
                  // }
                  name={item}
                  style={{
                    transform: "scale(0.6)",
                    // marginLeft: "10px",
                    paddingRight: "0px",
                  }}
                  sx={{
                    color: "red",
                    "&.Mui-checked": {
                      color: "#a6a6a6",
                    },
                  }}
                  onChange={(e) => handleCBChange(e)}
                />
              ) : (
                <Checkbox
                  checked={
                    filterFieldData.userSelection
                      ? filterFieldData.includeexclude === "Exclude"
                        ? filterFieldData.userSelection.includes(item)
                          ? true
                          : false
                        : false
                      : false
                  }
                  // indeterminate={
                  // 	// filterFieldData.userSelection
                  // 	// 	? filterFieldData.includeexclude === "Exclude"
                  // 	// 		?
                  // 	filterFieldData.userSelection.includes(item) ? true : false
                  // 	// 	: false
                  // 	// : false
                  // }
                  name={item}
                  style={{
                    transform: "scale(0.6)",
                    paddingRight: "0px",
                  }}
                  sx={{
                    // color: "red",
                    "&.Mui-checked": {
                      color: "orange",
                    },
                    // "&.MuiCheckbox-indeterminate": {
                    // 	color: "orange",
                    // },
                  }}
                  onChange={(e) => handleCBChange(e)}
                />
              )}

              <span
                title={item}
                style={{
                  marginLeft: 0,
                  marginTop: "3.5px",
                  justifySelf: "center",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                }}
              >
                {item}
              </span>
            </label>
          );
        }
      );
    } else {
      _selectionMembers = null;
    }

    return (
      <div className="SelectionMembersCheckBoxArea">{_selectionMembers}</div>
    );
  };

  ///Till Date Switch On Click
  const handleChangeTillDate = () => {
    if (filterFieldData.exprTypeTillDate === false) {
      filterFieldData["exprTypeTillDate"] = true;
    } else {
      filterFieldData["exprTypeTillDate"] = false;
    }
    // console.log(filterFieldData.userSelection);
    updateUserFilterItem(
      propKey,
      0,
      itemIndex,
      constructChartAxesFieldObject(),
      currentChartAxesName
    );
  };

  ///Render Upto Till Date Switch
  const SelecTillDate = () => {
    var labelTillDate = datePatternCollections.find(
      (item) => item.key === filterFieldData.prefix
    );
    var labelName = labelTillDate ? labelTillDate.value : null;
    if (labelName === "Year Quarter") {
      labelName = "Quarter";
    }
    if (labelName === "Year Month") {
      labelName = "Month";
    }
    return (
      // <Switch size="small" />
      <FormGroup
        sx={{
          marginLeft: "6px",
          paddingLeft: "10px",
          paddingBottom: "8px",
        }}
      >
        {/* {(filterFieldData.fieldtypeoption === "Search Condition" && */}
        {filterFieldData.prefix !== "date" &&
        ((filterFieldData.fieldtypeoption === "Search Condition" &&
          filterFieldData.switchEnableSearchCondition) ||
          (filterFieldData.fieldtypeoption === "Pick List" &&
            filterFieldData.switchenable !== "disabled")) ? (
          <FormControlLabel
            value="end"
            control={
              <GreenSwitch
                checked={filterFieldData.exprTypeTillDate}
                size="small"
                onChange={handleChangeTillDate}
              />
            }
            label={
              <Typography
                sx={{
                  fontSize: "13px",
                  paddingRight: "15px",
                }}
              >
                {labelName} Till Date
                {/* {datePatternCollections.find(
                  (item) => item.key === filterFieldData.prefix
                )} */}
              </Typography>
            }
            labelPlacement="end"
          />
        ) : (
          <FormControlLabel
            value="end"
            disabled
            control={
              <GreenSwitch
                // checked={filterFieldData.exprTypeTillDate}
                checked={false}
                size="small"
                onChange={handleChangeTillDate}
              />
            }
            label={
              <Typography
                sx={{
                  fontSize: "13px",
                  paddingRight: "15px",
                }}
              >
                {labelName} Till Date
              </Typography>
            }
            labelPlacement="end"
          />
        )}
      </FormGroup>
    );
  };

  ///Custom Green Switch
  const GreenSwitch = styled(Switch)(({ theme }) => ({
    "& .MuiSwitch-switchBase.Mui-checked": {
      // color: green[600],
      color: "#2bb9bb",
      "&:hover": {
        backgroundColor: alpha("#2bb9bb", theme.palette.action.hoverOpacity),
      },
    },
    "& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track": {
      backgroundColor: "#2bb9bb",
    },
  }));

  ///Initial Search Condition Values
  const initialSearchConditionValues = () => {
    if (filterFieldData.fieldtypeoption === "Search Condition") {
      if (dataType) {
        switch (filterFieldData.dataType) {
          case "decimal":
          case "integer":
            if (!filterFieldData.exprType) {
              filterFieldData["exprType"] = "greaterThan";
            }
            break;
          case "text":
            if (!filterFieldData.exprType) {
              filterFieldData["exprType"] = "beginsWith";
            }
            break;
          case "timestamp":
          case "date":
            if (!filterFieldData.exprType) {
              filterFieldData["prefix"] = "year";
              filterFieldData["exprType"] = "greaterThan";
            }
            break;
          default:
            if (!filterFieldData.exprType) {
              filterFieldData["exprType"] = "greaterThan";
            }
            break;
        }
      }
    }
  };

  ///Menu close event handler
  const handleClose = async (closeFrom: any, queryParam?: any) => {
    setAnchorEl(null);
    //setShowOptions(false);

    if (closeFrom === "opt2") {
      if (
        !filterFieldData.rawselectmembers ||
        filterFieldData.fieldtypeoption !== queryParam
      ) {
        filterFieldData["fieldtypeoption"] = queryParam;

        setLoading(true);
        await GetPickListItems();
        setLoading(false);
      }

      filterFieldData["fieldtypeoption"] = queryParam;

      if (filterFieldData.fieldtypeoption === "Pick List") {
        filterFieldData["userSelection"] = [
          ...filterFieldData.rawselectmembers,
        ];
        filterFieldData.isInValidData = false;
        filterFieldData.switchenable = "disabled";
        filterFieldData.exprTypeTillDate = false;
      } else if (filterFieldData.fieldtypeoption === "Search Condition") {
        initialSearchConditionValues();
        filterFieldData.exprTypeTillDate = false;
        checkForValidData();
      }
    } else if (closeFrom === "opt1") {
      if (filterFieldData.userSelection.includes("(All)")) {
        filterFieldData["userSelection"] = [];
      }
      filterFieldData.includeexclude = queryParam;
    }

    updateUserFilterItem(
      propKey,
      0,
      itemIndex,
      constructChartAxesFieldObject(),
      currentChartAxesName
    );
  };

  /// List of options to show at the end of each filter card
  const RenderMenu = () => {
    var options = ["Include", "Exclude"];
    var options2 = ["Pick List", "Search Condition"];

    return (
      <Menu
        id="basic-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={() => handleClose("clickOutside")}
        MenuListProps={{
          "aria-labelledby": "basic-button",
        }}
      >
        {options.length > 0
          ? options.map((opt, index) => {
              return (
                <div
                  style={{ display: "flex" }}
                  onClick={() => handleClose("opt1", opt)}
                >
                  <MenuItem
                    sx={{
                      fontSize: "12px",
                      alignSelf: "center",
                      padding: "2px 1rem",
                      flex: 1,
                    }}
                    key={index}
                  >
                    {opt}
                  </MenuItem>
                  {opt === filterFieldData.includeexclude ? (
                    <Tooltip title="Selected">
                      <DoneIcon
                        style={{
                          // flex: 1,
                          fontSize: "14px",
                          alignSelf: "center",
                          // float: "right",
                          marginRight: "5px",
                        }}
                      />
                    </Tooltip>
                  ) : null}
                </div>
              );
            })
          : null}
        <Divider />

        {options2.length > 0
          ? options2.map((opt2, index) => {
              return (
                <div
                  style={{ display: "flex" }}
                  onClick={() => handleClose("opt2", opt2)}
                >
                  <MenuItem
                    key={index}
                    sx={{
                      flex: 1,
                      fontSize: "12px",
                      alignSelf: "center",
                      padding: "2px 1rem",
                    }}
                  >
                    {opt2}
                  </MenuItem>
                  {opt2 === filterFieldData.fieldtypeoption ? (
                    <Tooltip title="Selected">
                      <DoneIcon
                        style={{
                          fontSize: "14px",
                          alignSelf: "center",
                          marginRight: "5px",
                        }}
                      />
                    </Tooltip>
                  ) : null}
                </div>
              );
            })
          : null}
      </Menu>
    );
  };

  ///set Search condition condition initiallize slider control
  const setSliderRange = () => {
    if (
      ["float", "decimal", "double", "integer"].includes(dataType) &&
      filterFieldData.exprType === "between"
    ) {
      if (
        filterFieldData.rawselectmembers &&
        !filterFieldData.greaterThanOrEqualTo &&
        !filterFieldData.lessThanOrEqualTo
      ) {
        filterFieldData.greaterThanOrEqualTo =
          filterFieldData.rawselectmembers[1];
        filterFieldData.lessThanOrEqualTo =
          filterFieldData.rawselectmembers[
            filterFieldData.rawselectmembers.length - 1
          ];
      }

      sliderRange = [
        filterFieldData.greaterThanOrEqualTo,
        filterFieldData.lessThanOrEqualTo,
      ];
    } else if (
      ["date", "timestamp"].includes(dataType) &&
      filterFieldData.prefix !== "date"
    ) {
      if (filterFieldData.prefix !== "year") {
        filterFieldData.greaterThanOrEqualTo = 1;
        filterFieldData.lessThanOrEqualTo =
          filterFieldData.rawselectmembers.length - 1;
      } else {
        filterFieldData.greaterThanOrEqualTo =
          filterFieldData.rawselectmembers[1];
        filterFieldData.lessThanOrEqualTo =
          filterFieldData.rawselectmembers[
            filterFieldData.rawselectmembers.length - 1
          ];
      }
    }
  };

  ///Search condition Silder on change handler
  // const handleSliderRangeOnChange = (event: any, newValue: any) => {
  // 	filterFieldData["greaterThanOrEqualTo"] = newValue[0];
  // 	filterFieldData["lessThanOrEqualTo"] = newValue[1];
  // 	sliderRange = newValue;
  // 	updateUserFilterItem(propKey, 0, itemIndex, constructChartAxesFieldObject(), currentChartAxesName);
  // };

  const checkValidDate = (val: any) => {
    if (
      ["date", "timestamp"].includes(dataType) &&
      filterFieldData.prefix === "date" &&
      val.includes("-")
    ) {
      return true;
    }

    return false;
  };

  const setDefaultDate = (key: string, value: any) => {
    if (filterFieldData[key]) {
      filterFieldData[key] = value ? value : new Date();
    }
  };

  const setSearchConditionDate = () => {
    if (
      ["date", "timestamp"].includes(dataType) &&
      filterFieldData.prefix === "date"
    ) {
      if (filterFieldData.exprType === "between") {
        if (checkValidDate(filterFieldData.exprInput)) {
          setDefaultDate("greaterThanOrEqualTo", filterFieldData.exprInput);
          setDefaultDate("lessThanOrEqualTo", filterFieldData.exprInput);
        }
      } else {
        if (checkValidDate(filterFieldData.lessThanOrEqualTo)) {
          setDefaultDate("exprInput", filterFieldData.lessThanOrEqualTo);
        }
      }
    }
  };

  ///Search Condition Dropdown list on change handler
  const handleDropDownForPatternOnChange = async (event: any) => {
    // let filterObj = userFilterGroup[propName].chartUserFilters.find((usrfilter) => usrfilter.uId === data.uid);

    filterFieldData["exprType"] = event.target.value;
    // filterFieldData = _modifiedResultForServerRequest(filterFieldData);

    if (filterFieldData.exprType === "between") {
      //setLoading(true);
      await GetPickListItems();
      setSliderRange();
      // setLoading(false);
    }

    setSearchConditionDate();

    if (filterFieldData.fieldtypeoption === "Search Condition") {
      if (filterFieldData.exprType === "between") {
        // if (
        //   !filterFieldData.greaterThanOrEqualTo.length ||
        //   !filterFieldData.lessThanOrEqualTo.length
        // ) {
        //   filterFieldData.switchEnableSearchCondition = false;
        // } else {
        filterFieldData.switchEnableSearchCondition = true;
        // }
      } else {
        if (!filterFieldData.exprInput || !filterFieldData.exprInput.length) {
          filterFieldData.switchEnableSearchCondition = false;
          filterFieldData.exprTypeTillDate = false;
        } else {
          filterFieldData.switchEnableSearchCondition = true;
        }
      }
    }

    updateUserFilterItem(
      propKey,
      0,
      itemIndex,
      constructChartAxesFieldObject(),
      currentChartAxesName
    );
  };

  ///Handle Menu button on click
  const handleClick = (event: any) => {
    setAnchorEl(event.currentTarget);
  };

  ///Remove filter card from dropzone
  const deleteItem = () => {
    deleteDropZoneItems(propKey, bIndex, itemIndex, currentChartAxesName);
  };

  ///Handle Date time grain dropdown list change
  const handleDropDownForDatePatternOnChange = async (event: any) => {
    filterFieldData["prefix"] = event.target.value;
    filterFieldData["greaterThanOrEqualTo"] = "";
    filterFieldData["lessThanOrEqualTo"] = "";
    filterFieldData.switchenable = "disabled";
    filterFieldData.exprTypeTillDate = false;
    if (!filterFieldData.exprInput) {
      filterFieldData["switchEnableSearchCondition"] = false;
      filterFieldData.exprTypeTillDate = false;
    }
    if (
      filterFieldData.fieldtypeoption === "Search Condition" &&
      event.target.value === "Date"
    ) {
      filterFieldData["exprInput"] = "";
    }
    if (
      filterFieldData.fieldtypeoption === "Search Condition" &&
      event.target.value !== "Date"
    ) {
      // if (filterFieldData["exprInput"].includes("-")) {
      //   filterFieldData["exprInput"] = "";
      //   filterFieldData["switchEnableSearchCondition"] = false;
      // }
    }

    // if (filterFieldData.fieldtypeoption === "Pick List") {
    setLoading(true);
    await GetPickListItems();
    setLoading(false);
    setSliderRange();
    // }
    setSearchConditionDate();

    updateUserFilterItem(
      propKey,
      0,
      itemIndex,
      constructChartAxesFieldObject(),
      currentChartAxesName
    );
  };

  const checkForValidData = () => {
    if (
      filterFieldData.prefix === "date" &&
      new Date(filterFieldData.greaterThanOrEqualTo) >
        new Date(filterFieldData.lessThanOrEqualTo)
    ) {
      filterFieldData["isInValidData"] = true;
    } else {
      if (
        parseInt(filterFieldData.greaterThanOrEqualTo) >
        parseInt(filterFieldData.lessThanOrEqualTo)
      ) {
        filterFieldData["isInValidData"] = true;
      }
    }
  };

  ///Search Condition user input change handler

  const handleCustomRequiredValueOnBlur = (
    val: number | string,
    key = "exprInput",
    type?: string
  ) => {
    // key = key || "exprInput";
    // if (key === "exprInput")
    filterFieldData["switchEnableSearchCondition"] = true;
    if (type && type === "date") {
      val = moment(val).format("yyyy-MM-DD");
    }

    if (!filterFieldData[key] || filterFieldData[key] !== val) {
      filterFieldData[key] = val;
      filterFieldData["isInValidData"] = false;

      if (key !== "exprInput") {
        checkForValidData();
      }

      if (
        key === "exprTnput" ||
        key === "greaterThanOrEqualTo" ||
        key === "lessThanOrEqualTo"
      ) {
        if (val === null || val === "" || val === undefined) {
          filterFieldData["switchEnableSearchCondition"] = false;
          filterFieldData.exprTypeTillDate = false;
        } else {
          filterFieldData["switchEnableSearchCondition"] = true;
        }
      }

      if (filterFieldData.exprType === "between") {
        sliderRange = [
          filterFieldData.greaterThanOrEqualTo,
          filterFieldData.lessThanOrEqualTo,
        ];
        if (
          !filterFieldData.greaterThanOrEqualTo.length ||
          !filterFieldData.lessThanOrEqualTo.length
          // val
        ) {
          //   filterFieldData["switchEnableSearchCondition"] = false;
          // } else {
          //   filterFieldData["switchEnableSearchCondition"] = true;
        }
      } else if (!filterFieldData.exprInput.length) {
        filterFieldData["switchEnableSearchCondition"] = false;
        filterFieldData.exprTypeTillDate = false;
      }

      updateUserFilterItem(
        propKey,
        0,
        itemIndex,
        constructChartAxesFieldObject(),
        currentChartAxesName
      );
    }
  };

  ///Render Search Condition Custom Input Control
  const SearchConditionCustomInputControl = ({ type }: any) => {
    return (
      <>
        <TextField
          InputProps={{
            style: {
              height: "25px",
              width: "100%",
              fontSize: "13px",
              marginRight: "30px",
            },
          }}
          placeholder="Value"
          defaultValue={filterFieldData.exprInput}
          type={type}
          onBlur={(e) => handleCustomRequiredValueOnBlur(e.target.value)}
        />
        {/* <input
					placeholder="Value"
					defaultValue={filterFieldData.exprInput}
					type={type}
					onBlur={e => handleCustomRequiredValueOnBlur(e.target.value)}
				/> */}

        {filterFieldData.isInValidData ? (
          <span className="ErrorText">Please enter valid data.</span>
        ) : null}
      </>
    );
  };

  ///Render Search Condition Between Control
  const SearchConditionBetweenControl = () => {
    return (
      <>
        {/*<StyledSlider
          value={sliderRange}
          onChange={handleSliderRangeOnChange}
          min={filterFieldData.greaterThanOrEqualTo}
          max={filterFieldData.lessThanOrEqualTo}
          marks={_marks}
           />*/}
        <TextField
          type="number"
          className="CustomInputValue"
          sx={{
            width: "100%",
          }}
          defaultValue={filterFieldData.greaterThanOrEqualTo}
          onBlur={(e) => {
            handleCustomRequiredValueOnBlur(
              e.target.value,
              "greaterThanOrEqualTo"
            );
          }}
        />
        <TextField
          type="number"
          className="CustomInputValue"
          defaultValue={filterFieldData.lessThanOrEqualTo}
          onBlur={(e) => {
            handleCustomRequiredValueOnBlur(
              e.target.value,
              "lessThanOrEqualTo"
            );
          }}
        />
        {/* <input
					placeholder="Greater than or Equal to"
					type="number"
					className="CustomInputValue"
					defaultValue={filterFieldData.greaterThanOrEqualTo}
					onBlur={e => {
						handleCustomRequiredValueOnBlur(e.target.value, "greaterThanOrEqualTo");
					}}
				/>
				<input
					placeholder="Less than or Equal to"
					type="number"
					className="CustomInputValue"
					defaultValue={filterFieldData.lessThanOrEqualTo}
					onBlur={e => {
						handleCustomRequiredValueOnBlur(e.target.value, "lessThanOrEqualTo");
					}}
				/> */}
        {filterFieldData.isInValidData ? (
          <span className="ErrorText">Please enter valid data.</span>
        ) : null}
      </>
    );
  };

  ///Render Search Condition Date Between Control
  const SearchConditionDateBetween = () => {
    return (
      <div className="customDatePickerWidth">
        <LocalizationProvider dateAdapter={AdapterDateFns}>
          <DatePicker
            value={filterFieldData.greaterThanOrEqualTo}
            onChange={(e) =>
              handleCustomRequiredValueOnBlur(e, "greaterThanOrEqualTo", "date")
            }
            renderInput={(params) => <TextField {...params} />}
          />
        </LocalizationProvider>

        <LocalizationProvider dateAdapter={AdapterDateFns}>
          <DatePicker
            value={filterFieldData.lessThanOrEqualTo}
            onChange={(e) =>
              handleCustomRequiredValueOnBlur(e, "lessThanOrEqualTo", "date")
            }
            renderInput={(params) => <TextField {...params} />}
          />
        </LocalizationProvider>
        {filterFieldData.isInValidData ? (
          <span className="ErrorText">Please enter valid data.</span>
        ) : null}
      </div>
    );
  };

  ///Render Search condition user input control
  const CustomRequiredField = () => {
    var members = null;

    if (dataType) {
      switch (dataType) {
        case "decimal":
        case "float":
        case "double":
        case "integer":
          if (filterFieldData.exprType === "between") {
            members = (
              <SearchConditionBetweenControl></SearchConditionBetweenControl>
            );
          } else {
            members = (
              <SearchConditionCustomInputControl type="number"></SearchConditionCustomInputControl>
            );
          }
          break;
        case "text":
          members = (
            <SearchConditionCustomInputControl type="text"></SearchConditionCustomInputControl>
          );
          break;
        case "date":
        case "timestamp":
          if (filterFieldData.prefix === "date") {
            if (filterFieldData.exprType === "between") {
              members = (
                <SearchConditionDateBetween></SearchConditionDateBetween>
              );
            } else {
              members = (
                <div className="customDatePickerWidth">
                  <LocalizationProvider dateAdapter={AdapterDateFns}>
                    <DatePicker
                      value={filterFieldData.exprInput}
                      onChange={(e) =>
                        handleCustomRequiredValueOnBlur(e, "exprInput", "date")
                      }
                      renderInput={(params) => <TextField {...params} />}
                    />
                  </LocalizationProvider>
                  {filterFieldData.isInValidData ? (
                    <span className="ErrorText">Please enter valid data.</span>
                  ) : null}
                </div>
              );
            }
          } else {
            if (filterFieldData.exprType === "between") {
              members = (
                <SearchConditionBetweenControl></SearchConditionBetweenControl>
              );
            } else {
              members = (
                <SearchConditionCustomInputControl type="number"></SearchConditionCustomInputControl>
              );
            }
          }
          break;
        default:
          members = null;
          break;
      }
    }

    return <div>{members}</div>;
  };

  ///Dropdown list to select Time grain
  const DropDownForDatePattern = ({ items }: any) => {
    return (
      <Select
        // sx={{ height: "1.5rem", fontSize: "14px", textAlign: "left", width: "100%" }}
        sx={{
          height: "1.5rem",
          fontSize: "14px",
          textAlign: "left",
        }}
        IconComponent={KeyboardArrowDownIcon}
        onChange={(e) => {
          handleDropDownForDatePatternOnChange(e);
        }}
        value={filterFieldData["prefix"]}
      >
        {items.map((item: any) => {
          return (
            <MenuItem
              key={item.key}
              value={item.key}
              selected={item.key === filterFieldData.exprType}
            >
              <Typography
                sx={{
                  width: "80px",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  fontSize: "12px",
                  lineHeight: "20px",
                }}
              >
                {item.value}
              </Typography>
            </MenuItem>
          );
        })}
      </Select>
    );
  };

  ///Search Condition Dropdown list to select condition
  const DropDownForPattern = ({ items }: any) => {
    return (
      <Select
        sx={{
          height: "1.5rem",
          fontSize: "14px",
          textAlign: "left",
        }}
        IconComponent={KeyboardArrowDownIcon}
        onChange={(e) => {
          handleDropDownForPatternOnChange(e);
        }}
        value={filterFieldData.exprType}
      >
        {items.map((item: any) => {
          return (
            <MenuItem
              key={item.key}
              value={item.key}
              // selected={item.key === filterFieldData.exprType}
            >
              <Typography
                sx={{
                  width: "auto",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  fontSize: "12px",
                }}
              >
                {item.value}
              </Typography>
              {/* CustomCard */}
            </MenuItem>
          );
        })}
      </Select>
    );
  };

  ///Reder Search condition card controls
  const CustomCard = () => {
    var members = null;
    if (field.dataType) {
      switch (field.dataType) {
        case "decimal":
        case "integer":
          members = (
            <DropDownForPattern
              items={equalPatternCollections}
            ></DropDownForPattern>
          );
          break;
        case "text":
          members = (
            <DropDownForPattern
              items={withPatternCollections}
            ></DropDownForPattern>
          );
          break;
        case "timestamp":
        case "date":
          members = (
            <DropDownForPattern
              items={equalPatternCollections}
            ></DropDownForPattern>
          );
          break;
        default:
          members = null;
          break;
      }
    }

    return (
      <div
        // className="CustomRequiredField"
        style={{
          backgroundColor: "white",
          display: "flex",
          flexDirection: "column",
          rowGap: "8px",
          marginLeft: "6px",
          width: "94%",
        }}
      >
        {members}
        <CustomRequiredField></CustomRequiredField>
      </div>
    );
  };

  ///Expand Collapse Icon switch
  const ExpandCollaseIconSwitch = () => {
    return filterFieldData.isCollapsed ? (
      <ChevronRightIcon
        style={{ height: "18px", width: "18px", color: "#999999" }}
        onClick={(e) => {
          filterFieldData.isCollapsed = false;
          updateUserFilterItem(
            propKey,
            0,
            itemIndex,
            constructChartAxesFieldObject(),
            currentChartAxesName
          );
        }}
      />
    ) : (
      <KeyboardArrowDownIcon
        style={{ height: "18px", width: "18px", color: "#999999" }}
        onClick={(e) => {
          filterFieldData.isCollapsed = true;
          updateUserFilterItem(
            propKey,
            0,
            itemIndex,
            constructChartAxesFieldObject(),
            currentChartAxesName
          );
        }}
      />
    );
  };

  ///Construct Chart property object
  const constructChartAxesFieldObject = () => {
    return filterFieldData;
  };

  // const styles = {
  //   ...(!filterFieldData.isCollapsed
  //     ? { border: "2px purple solid", color: "purple" }
  //     : {}),
  // };

  return (
    <div
      ref={(node: any) => drag(drop(node))}
      className="UserFilterCard"
      style={
        filterFieldData.isInValidData
          ? { border: "2px red solid", backgroundColor: "lightpink" }
          : {}
      }
      // style={styles}
    >
      {loading ? <LoadingPopover /> : null}

      <div
        className="axisFilterField"
        // style={styles}
        style={
          !filterFieldData.isCollapsed
            ? {
                border: "1px #af99db solid",
                color: "#af99db",
                fontWeight: "bold",
              }
            : {}
        }
      >
        {/* remove column  */}
        <button
          type="button"
          className="buttonCommon columnClose"
          onClick={deleteItem}
          title="Remove field"
        >
          <CloseRoundedIcon style={{ fontSize: "13px" }} />
        </button>

        {/* filter column name */}

        <span
          className="columnName"
          style={
            filterFieldData.includeexclude === "Exclude"
              ? { border: "#ffb74d 1px solid", lineHeight: "15px" }
              : { lineHeight: "15px" }
          }
        >
          {field.fieldname}
        </span>
        {/* down arrow icon */}
        <button
          type="button"
          className="buttonCommon"
          style={{ backgroundColor: "transparent" }}
          title="More Options"
          onClick={handleClick}
        >
          <MoreVertIcon style={{ fontSize: "16px", color: "#999999" }} />
        </button>

        {/* expand colapse icon */}
        <button
          type="button"
          className="buttonCommon columnDown"
          title={filterFieldData.isCollapsed ? "Expand" : "Collapse"}
        >
          <ExpandCollaseIconSwitch />
        </button>

        <RenderMenu />
      </div>

      {!filterFieldData.isCollapsed ? (
        <>
          <div
            className="UserSelectionDiv"
            style={
              filterFieldData.isInValidData
                ? { border: "2px red solid", backgroundColor: "lightpink" }
                : {}
            }
          >
            {filterFieldData.dataType === "timestamp" ||
            filterFieldData.dataType === "date" ? (
              <div className="CustomRequiredField">
                {filterFieldData.fieldtypeoption === "Pick List" ? (
                  <DropDownForDatePattern
                    items={datePatternCollections}
                  ></DropDownForDatePattern>
                ) : (
                  <DropDownForDatePattern
                    items={datePatternSearchConditionCollections}
                  ></DropDownForDatePattern>
                )}
              </div>
            ) : null}
            {filterFieldData.fieldtypeoption === "Pick List" ? (
              <>
                <SelecPickListCard></SelecPickListCard>
                {filterFieldData.dataType === "timestamp" ||
                filterFieldData.dataType === "date" ? (
                  <SelecTillDate></SelecTillDate>
                ) : null}
              </>
            ) : (
              <>
                <CustomCard></CustomCard>
                {filterFieldData.dataType === "timestamp" ||
                filterFieldData.dataType === "date" ? (
                  <SelecTillDate></SelecTillDate>
                ) : null}
              </>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
};

const mapStateToProps = (
  state: TabTileStateProps2 & ChartPropertiesStateProps & isLoggedProps,
  ownProps: any
) => {
  return {
    chartProp: state.chartProperties,
    token: state.isLogged.accessToken,
  };
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
  return {
    updateLeftFilterItem: (
      propKey: string,
      binIndex: number,
      itemIndex: number,
      item: any,
      currentChartAxesName: string
    ) =>
      dispatch(
        editChartPropItem("updateQuery", {
          propKey,
          binIndex,
          itemIndex,
          item,
          currentChartAxesName,
        })
      ),
    updtateFilterExpandeCollapse: (
      propKey: string,
      bIndex: number,
      item: any,
      currentChartAxesName: string
    ) =>
      dispatch(
        updtateFilterExpandeCollapse(
          propKey,
          bIndex,
          item,
          currentChartAxesName
        )
      ),
    deleteDropZoneItems: (
      propKey: string,
      binIndex: number,
      itemIndex: any,
      currentChartAxesName: string
    ) =>
      dispatch(
        editChartPropItem("delete", {
          propKey,
          binIndex,
          itemIndex,
          currentChartAxesName,
        })
      ),

    sortAxes: (
      propKey: string,
      bIndex: number,
      dragUId: any,
      uId: string,
      currentChartAxesName: string
    ) =>
      dispatch(sortAxes(propKey, bIndex, dragUId, uId, currentChartAxesName)),
    revertAxes: (
      propKey: string,
      bIndex: number,
      uId: string,
      originalIndex: any,
      currentChartAxesName: string
    ) =>
      dispatch(
        revertAxes(propKey, bIndex, uId, originalIndex, currentChartAxesName)
      ),
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(UserFilterCard);
