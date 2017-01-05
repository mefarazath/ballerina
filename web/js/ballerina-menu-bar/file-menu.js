/**
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

define(([],function (){
    var FileMenu = {
        id: "FileMenu",
        label: "File",
        items: [
            {
                id: "FileMenu-NewFile",
                label: "New",
                action: "create-new-tab",
                disabled: false
            },
            {
                id: "FileMenu-OpenFile",
                label: "Open",
                action: "open-file-open-dialog",
                disabled: true
            },
            {
                id: "FileMenu-SaveFile",
                label: "Save",
                action: "open-file-save-dialog",
                disabled: false
            }

            ],

    };

    return FileMenu;
}));