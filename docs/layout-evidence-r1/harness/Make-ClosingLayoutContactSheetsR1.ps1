param([string[]]$Cases=@('phone-large-dark','phone-small-light','phone-landscape-light','tablet-light','tablet-dark-large','tablet-landscape-dark'))
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot='D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-layout-r1'
foreach($taskCode in $Cases){
 $taskLand=$taskCode.Contains('landscape');$taskW=if($taskLand){720}else{360};$taskH=if($taskLand){375}else{780}
 $taskCanvas=New-Object Drawing.Bitmap ($taskW*3),($taskH*3)
 $taskG=[Drawing.Graphics]::FromImage($taskCanvas);$taskG.Clear([Drawing.Color]::White);$taskFont=New-Object Drawing.Font 'Arial',12
 try{
  $taskScenes=@('login','overview','input','unsaved','settings-review','team-form','catalog-form')
  for($taskI=0;$taskI -lt $taskScenes.Count;$taskI++){
   $taskImage=[Drawing.Image]::FromFile("$taskRoot/$taskCode/$($taskScenes[$taskI]).png")
   try{
    $taskX=$taskW*($taskI%3);$taskY=$taskH*[math]::Floor($taskI/3);$taskRatio=[math]::Min(($taskW-10)/$taskImage.Width,($taskH-40)/$taskImage.Height)
    $taskG.DrawString($taskScenes[$taskI],$taskFont,[Drawing.Brushes]::Black,$taskX+5,$taskY+4)
    $taskG.DrawImage($taskImage,[int]$taskX,[int]($taskY+30),[int]($taskImage.Width*$taskRatio),[int]($taskImage.Height*$taskRatio))
   }finally{$taskImage.Dispose()}
  }
  $taskCanvas.Save("$taskRoot/$taskCode-sheet.png")
 }finally{$taskG.Dispose();$taskCanvas.Dispose();$taskFont.Dispose()}
}
