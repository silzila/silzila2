import {
	ChartControl,
	ChartControlStateProps,
} from "../../redux/ChartPoperties/ChartControlsInterface";
import {
	ChartPropertiesProps,
	ChartPropertiesStateProps,
} from "../../redux/ChartPoperties/ChartPropertiesInterfaces";
import { PlayBookStateProps } from "../../redux/PlayBook/PlayBookInterfaces";
import { TabStateProps, TabStateProps2 } from "../../redux/TabTile/TabStateInterfaces";
import { TabTileStateProps, TabTileStateProps2 } from "../../redux/TabTile/TabTilePropsInterfaces";
import { TileStateProps, TileStateProps2 } from "../../redux/TabTile/TileStateInterfaces";
import { isLoggedProps } from "../../redux/UserInfo/IsLoggedInterfaces";
import { PlayBookProps } from "../DataConnection/PlayBookInterfaces";

export type MapStateProps = isLoggedProps &
	TabStateProps2 &
	TileStateProps2 &
	TabTileStateProps2 &
	PlayBookStateProps &
	ChartPropertiesStateProps &
	ChartControlStateProps;

export interface MenubarProps {
	//props
	from: string;
	//state
	token: string;
	tabTileProps: TabTileStateProps;
	tabState: TabStateProps;
	tileState: TileStateProps;
	playBookState: any;
	chartControl: ChartControl;
	chartProperty: ChartPropertiesProps;
	//Dispatch
	resetUser: () => void;
	updatePlayBookId: (
		playBookName: string,
		playBookUid: string,
		description: string,
		oldContent: string | any
	) => void;
	resetAllStates: () => void;
	toggleDashMode: (dashMode: string) => void;
	toggleDashModeInTab: (tabId: number, dashMode: string) => void;
}
