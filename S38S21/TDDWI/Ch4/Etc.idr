module S38S21.TDDWI.Ch4.Etc

maxMaybe : Ord n => Maybe n -> Maybe n -> Maybe n
maxMaybe Nothing Nothing = Nothing
maxMaybe Nothing (Just b) = Just b
maxMaybe (Just a) Nothing = Just a
maxMaybe (Just a) (Just b) = if a > b then Just a else Just b
