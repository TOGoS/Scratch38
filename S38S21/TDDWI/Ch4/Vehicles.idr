module S38S21.TDDWI.Ch4.Vehicles

data PowerSource = Pedal | Petrol

data Vehicle : PowerSource -> Type where
	-- The stuff under 'where' can be types...
	Bicycle : Vehicle Pedal
	Car : (fuel : Nat) -> Vehicle Petrol
	Bus : (fuel : Nat) -> Vehicle Petrol
	-- Or more [Vehicle-related] type constructors!
	ModularTrike : (pwr : PowerSource) -> Nat -> Vehicle pwr
	-- but we're not allowed to declare constructors for non-Vehicle things:
	-- ActuallyAList : List Nat
	
	-- Maybe the rule is that you can have as many type parameters
	-- for each case as you want, but the last one has to call the new
	-- type constructor being defined with all its arguments.

wheelCount : Vehicle power -> Nat
wheelCount Bicycle = 2
wheelCount (Car fuel) = 4
wheelCount (Bus fuel) = 4
wheelCount (ModularTrike pwr fuel) = 3

refuel : Vehicle Petrol -> Vehicle Petrol
refuel (Car _) = Car 100
refuel (Bus _) = Car 200
refuel (ModularTrike Petrol fuel) = ModularTrike Petrol 100
-- Optional sanity check; replace `= whatever` with `impossible` for
-- cases that you intend to be impossible based on types:
refuel Bicycle impossible
refuel (ModularTrike Pedal fuel) impossible
